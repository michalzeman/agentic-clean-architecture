package mz.shared.connector.nats.jetstream.consumer

import io.nats.client.Connection
import io.nats.client.JetStream
import io.nats.client.JetStreamSubscription
import io.nats.client.Message
import io.nats.client.PullSubscribeOptions
import io.nats.client.api.ConsumerConfiguration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import mz.shared.connector.nats.NatsConnectionFactory
import mz.shared.connector.nats.jetstream.message.JetStreamMessageConverter
import mz.shared.connector.observability.TraceParent
import org.slf4j.LoggerFactory
import org.springframework.context.SmartLifecycle
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Contract for JetStream message consumers.
 *
 * JetStream pull consumers are recommended for new projects as they provide:
 * - Automatic load balancing across multiple subscribers
 * - Better flow control via client-controlled batch fetching
 * - No need for complex queue group configuration
 * - Durable consumers persist delivery state server-side
 *
 * This interface extends [SmartLifecycle] to ensure Spring's LifecycleProcessor
 * automatically manages consumer startup/shutdown via the [start]/[stop] contract.
 *
 * @param T The type of message payload this consumer handles
 */
interface JetStreamConsumer<T> : SmartLifecycle {
    /**
     * The unique name of this consumer configuration.
     * Used to identify and share durable consumers across instances.
     */
    val consumerName: String

    /**
     * The stream name this consumer subscribes to.
     */
    val streamName: String

    /**
     * The subject filter for message selection.
     * Supports wildcards (e.g., "orders.*", "events.>")
     */
    val subjectFilter: String

    /**
     * Acknowledges successful processing of a message.
     * This tells JetStream the message can be removed from pending.
     */
    suspend fun acknowledge(message: JetStreamMessage<T>)

    /**
     * Negatively acknowledges a message, triggering redelivery.
     * Use when processing fails and message should be retried.
     */
    suspend fun negativeAcknowledge(message: JetStreamMessage<T>)

    /**
     * Indicates if this consumer should be durable.
     * Durable consumers persist delivery state across restarts.
     */
    val isDurable: Boolean
        get() = true
}

/**
 * Represents a consumed JetStream message with its metadata.
 */
data class JetStreamMessage<T>(
    val payload: T,
    val subject: String,
    val messageId: String,
    val sequenceNumber: Long,
    val timestamp: java.time.Instant,
    val headers: Map<String, List<String>> = emptyMap(),
    val deliveryCount: Int = 1,
    val rawMessage: Any? = null, // Platform-specific message reference
)

/**
 * Exception thrown during message consumption.
 */
class JetStreamConsumeException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/**
 * Returns `true` when a message has reached its final allowed delivery, i.e. the server
 * will drop it instead of redelivering, even if it is NAKed.
 */
internal fun isTerminalDelivery(
    deliveredCount: Long,
    maxDeliver: Int,
): Boolean = deliveredCount >= maxDeliver

/**
 * Default implementation of [JetStreamConsumer] using NATS JetStream pull consumers.
 *
 * This implementation uses pull-based message consumption which provides better
 * flow control and horizontal scaling capabilities compared to push consumers.
 *
 * Consumption starts via SmartLifecycle.start() at default phase (Int.MAX_VALUE),
 * ensuring it runs AFTER all other beans including stream initialization (phase 0).
 *
 * @param T The type of message payload
 * @param consumerName Unique name for this consumer (used for durable consumer identification)
 * @param streamName The JetStream stream to consume from
 * @param subjectFilter Subject pattern to filter messages (supports wildcards)
 * @param connectionFactory Factory for obtaining NATS connections
 * @param converter Message converter for serialization/deserialization
 * @param onMessage Callback function invoked for each consumed message
 * @param batchSize Number of messages to fetch in each batch (default: 100)
 * @param durable Whether this consumer should be durable (default: true)
 * @param maxAckPending Maximum number of pending acknowledgments (default: 1000)
 * @param maxWaitMillis Maximum time to wait for messages in a fetch operation (default: 5000ms)
 * @param fetchIntervalMillis Interval between fetch operations when no messages (default: 100ms)
 * @param ackWaitSeconds How long the server waits for an ack before redelivering (default: 30s)
 * @param maxDeliver Maximum redelivery attempts before the message is considered failed (default: 3)
 */
internal class DefaultJetStreamConsumer<T>(
    override val consumerName: String,
    override val streamName: String,
    override val subjectFilter: String,
    private val connectionFactory: NatsConnectionFactory,
    private val converter: JetStreamMessageConverter<T>,
    private val onMessage: suspend (JetStreamMessage<T>) -> Unit,
    private val autoAck: Boolean = false,
    private val batchSize: Int = 100,
    override val isDurable: Boolean = true,
    private val maxAckPending: Int = 1000,
    private val maxWaitMillis: Long = 5000L,
    private val fetchIntervalMillis: Long = 100L,
    private val ackWaitSeconds: Long = 30L,
    private val maxDeliver: Int = 3,
    private val connectedGateTimeoutMs: Long = 15_000L,
    private val connectedGatePollIntervalMs: Long = 500L,
    private val subscribeTimeoutMs: Long = 15_000L,
    private val resubscribeMaxAttempts: Int = 5,
    private val resubscribeBackoffMillis: Long = 1_000L,
    private val backoffDelay: suspend (Long) -> Unit = { delay(it) },
    private val coroutineExceptionHandler: CoroutineExceptionHandler =
        CoroutineExceptionHandler { _, t ->
            LoggerFactory
                .getLogger(DefaultJetStreamConsumer::class.java)
                .error("JetStream consumer '{}' terminated unexpectedly", consumerName, t)
        },
    private val traceParent: TraceParent? = null,
) : JetStreamConsumer<T>,
    SmartLifecycle {
    private val logger = LoggerFactory.getLogger(DefaultJetStreamConsumer::class.java)

    companion object {
        private const val ERROR_BACKOFF_MULTIPLIER = 5L

        /** Consecutive fetch errors before the subscription is torn down and re-created. */
        private const val FETCH_ERROR_RESUBSCRIBE_THRESHOLD = 3

        /** Upper bound for draining a subscription during teardown. */
        private val RESUBSCRIBE_DRAIN_TIMEOUT = Duration.ofSeconds(5)
    }

    // Holds the subscription reference once initialized; null until first use
    private val subscriptionRef = AtomicReference<JetStreamSubscription?>(null)

    // Guards concurrent getOrCreateSubscription() calls so only one creates the subscription
    private val subscriptionMutex = Mutex()

    /**
     * Returns the cached subscription or creates one on first access.
     * [subscriptionMutex] serializes creation against [resubscribe] and [stopConsuming]
     * so at most one live subscription exists at any time.
     */
    private suspend fun getOrCreateSubscription(): JetStreamSubscription =
        subscriptionRef.get() ?: subscriptionMutex.withLock {
            subscriptionRef.get() ?: createSubscriptionUnlessStopped()
        }

    /**
     * Creates a subscription while holding [subscriptionMutex]. If the consumer was
     * stopped while the subscription was being created, the freshly created
     * subscription is drained immediately instead of being stored — a stopped
     * consumer must not leak a live subscription on the shared durable consumer.
     */
    private suspend fun createSubscriptionUnlessStopped(): JetStreamSubscription {
        val created = createSubscription()
        if (!isRunning.get()) {
            drainSubscription(created, "stop")
            throw CancellationException("Consumer stopped while creating subscription: consumer=$consumerName")
        }
        subscriptionRef.set(created)
        return created
    }

    // Mutable because a cancelled SupervisorJob cannot be reused: each start() creates
    // a fresh scope so start() after stop() can launch consumption again.
    // Volatile because start()/stop() may run on different Spring lifecycle threads.
    @Volatile
    private var scope: CoroutineScope? = null

    private val isRunning = AtomicBoolean(false)

    private val consumerStarted = AtomicBoolean(false)

    // Tracks number of NAK failures to aid diagnosis of message storms
    private val nakFailureCount = AtomicLong(0)

    /**
     * SmartLifecycle start() - starts message consumption.
     * Called by Spring at application startup (default phase Int.MAX_VALUE),
     * after all other beans including stream initialization (phase 0).
     */
    override fun start() {
        logger.info("Starting JetStream consumer: consumer={}, stream={}, subject={}", consumerName, streamName, subjectFilter)
        if (consumerStarted.getAndSet(true)) {
            logger.debug("Consumer already started: consumer={}", consumerName)
            return
        }
        isRunning.set(true)
        startConsuming()
        logger.info("JetStream consumer startup initiated: consumer={}", consumerName)
    }

    /**
     * SmartLifecycle stop() - stops message consumption and cleanup.
     * Called by Spring during application shutdown.
     */
    override fun stop() {
        stopConsuming()
    }

    /**
     * SmartLifecycle isRunning() - checks if consumer is currently consuming.
     */
    override fun isRunning(): Boolean = isRunning.get()

    /**
     * SmartLifecycle getPhase() - defines startup/shutdown order.
     * Using default (Int.MAX_VALUE) ensures this starts AFTER stream initialization (phase 0).
     */
    override fun getPhase(): Int = Int.MAX_VALUE

    /**
     * Creates the JetStream pull subscription with appropriate consumer configuration.
     * Enforces a CONNECTED gate (polls until connection is CONNECTED, bounded by [connectedGateTimeoutMs])
     * and wraps the blocking subscribe + flush in a bounded timeout ([subscribeTimeoutMs]).
     */
    private suspend fun createSubscription(): JetStreamSubscription =
        try {
            val connection = connectionFactory.getConnection()

            waitForConnected(connection)

            val jetStream: JetStream = connection.jetStream()

            // Build consumer configuration
            val consumerConfigBuilder =
                ConsumerConfiguration
                    .builder()
                    .durable(consumerName)
                    .ackWait(Duration.ofSeconds(ackWaitSeconds))
                    .maxDeliver(maxDeliver.toLong())
                    .maxAckPending(maxAckPending.toLong())

            // Build pull subscription options
            val pullOptions =
                PullSubscribeOptions
                    .builder()
                    .configuration(consumerConfigBuilder.build())
                    .build()

            logger.info(
                "Connection status before subscribe: consumer={}, stream={}, subject={}, status={}",
                consumerName,
                streamName,
                subjectFilter,
                connection.status,
            )

            // Bound the blocking subscribe + flush so they cannot block indefinitely.
            // Note: runInterruptible sends Thread.interrupt() on timeout, but if the NATS
            // client ignores interrupts the Dispatchers.IO thread may outlive the timeout.
            // The bound guarantees the coroutine fails fast, not that the blocked thread dies.
            val sub =
                withTimeoutOrNull(subscribeTimeoutMs) {
                    runInterruptible(Dispatchers.IO) {
                        val s = jetStream.subscribe(subjectFilter, pullOptions)
                        connection.flush(Duration.ofSeconds(1))
                        s
                    }
                } ?: throw JetStreamConsumeException(
                    "Timed out creating JetStream subscription (${subscribeTimeoutMs}ms): " +
                        "consumer=$consumerName, stream=$streamName, subject=$subjectFilter, " +
                        "connectionStatus=${connection.status}",
                )

            logger.info(
                "Created JetStream pull consumer: consumer={}, stream={}, subject={}, durable={}, connectionStatus={}",
                consumerName,
                streamName,
                subjectFilter,
                isDurable,
                connection.status,
            )

            sub
        } catch (e: JetStreamConsumeException) {
            throw e
        } catch (e: Exception) {
            throw JetStreamConsumeException(
                "Failed to create JetStream consumer subscription: consumer=$consumerName, stream=$streamName, subject=$subjectFilter",
                e,
            )
        }

    /**
     * Waits for the NATS connection to reach [Connection.Status.CONNECTED].
     * Polls every [connectedGatePollIntervalMs] up to [connectedGateTimeoutMs] total.
     * Throws [JetStreamConsumeException] with diagnostic details if the bound is exceeded.
     */
    private suspend fun waitForConnected(connection: Connection) {
        val deadline = System.currentTimeMillis() + connectedGateTimeoutMs
        while (connection.status != Connection.Status.CONNECTED) {
            if (System.currentTimeMillis() >= deadline) {
                throw JetStreamConsumeException(
                    "Connection not CONNECTED after ${connectedGateTimeoutMs}ms: " +
                        "consumer=$consumerName, stream=$streamName, status=${connection.status}",
                )
            }
            logger.info(
                "Waiting for connection to be CONNECTED: consumer={}, currentStatus={}",
                consumerName,
                connection.status,
            )
            delay(connectedGatePollIntervalMs)
        }
    }

    /**
     * Starts consuming messages in a background coroutine.
     * This is called automatically during initialization.
     */
    private fun startConsuming() {
        val newScope =
            CoroutineScope(
                SupervisorJob() + Dispatchers.IO + coroutineExceptionHandler,
            )
        scope = newScope
        newScope.launch {
            try {
                consumeMessages()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // Catch Throwable (not just Error) so that JetStreamConsumeException —
                // a RuntimeException thrown by the CONNECTED gate or bounded subscribe —
                // also marks the consumer as stopped before propagating to the handler.
                // Reset the one-shot guard too, so a later start() retries the failed
                // startup instead of being permanently blocked by it.
                isRunning.set(false)
                consumerStarted.set(false)
                throw e
            }
        }
    }

    private suspend fun CoroutineScope.consumeMessages() {
        logger.debug("Starting message consumption: consumer={}", consumerName)

        var consecutiveFetchErrors = 0

        while (isActive && isRunning.get()) {
            // A subscription that was drained or closed must never be fetched from:
            // re-create it via the self-healing resubscribe path.
            val subscription = getOrCreateSubscription()
            if (!subscription.isActive) {
                logger.warn("JetStream subscription is no longer active: consumer={}", consumerName)
                if (!resubscribe()) return
                consecutiveFetchErrors = 0
                continue
            }

            try {
                val messages = fetchMessages(subscription)
                consecutiveFetchErrors = 0

                if (messages.isEmpty()) {
                    // No messages available, wait before next fetch
                    delay(fetchIntervalMillis)
                    continue
                }

                logger.debug("Fetched {} messages from stream {}", messages.size, streamName)

                // Process each message
                processMessages(messages)
            } catch (e: CancellationException) {
                // Normal cancellation, exit cleanly
                logger.debug("Message consumption cancelled: consumer={}", consumerName)
                throw e
            } catch (e: Exception) {
                consecutiveFetchErrors++
                logger.error(
                    "Error during message consumption: consumer={}, consecutiveErrors={}/{}",
                    consumerName,
                    consecutiveFetchErrors,
                    FETCH_ERROR_RESUBSCRIBE_THRESHOLD,
                    e,
                )

                if (consecutiveFetchErrors >= FETCH_ERROR_RESUBSCRIBE_THRESHOLD) {
                    // The subscription is likely dead: tear it down and re-create it
                    // instead of spinning on a failing fetch forever.
                    if (!resubscribe()) return
                    consecutiveFetchErrors = 0
                } else {
                    // Brief delay before retry to avoid tight error loops
                    delay(fetchIntervalMillis * ERROR_BACKOFF_MULTIPLIER)
                }
            }
        }
    }

    /**
     * Fetches a batch of messages from JetStream, returning an empty list on a null result.
     */
    private suspend fun fetchMessages(subscription: JetStreamSubscription): List<Message> =
        withContext(Dispatchers.IO) {
            subscription.fetch(batchSize, Duration.ofMillis(maxWaitMillis))
        }.takeIf { it != null }
            ?: emptyList()

    /**
     * Tears down the current subscription and re-creates it, bounded by [resubscribeMaxAttempts]
     * with exponential backoff starting at [resubscribeBackoffMillis].
     * Returns false when all attempts are exhausted; the consumer is then marked stopped so
     * [JetStreamConsumerHealthIndicator] reports DOWN instead of failing silently.
     */
    private suspend fun CoroutineScope.resubscribe(): Boolean {
        var attempt = 1
        var backoffMillis = resubscribeBackoffMillis
        while (attempt <= resubscribeMaxAttempts) {
            logger.warn(
                "Resubscribing JetStream consumer (attempt {}/{}): consumer={}, stream={}, subject={}",
                attempt,
                resubscribeMaxAttempts,
                consumerName,
                streamName,
                subjectFilter,
            )
            try {
                // Teardown and creation are atomic under [subscriptionMutex]: a concurrent
                // stop() either drains the subscription this attempt is about to store,
                // or runs before it — never both alive and never neither.
                subscriptionMutex.withLock {
                    teardownSubscriptionLocked("resubscribe")
                    createSubscriptionUnlessStopped()
                }
                logger.info("JetStream consumer resubscribed: consumer={}, attempt={}", consumerName, attempt)
                return true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.warn(
                    "Resubscribe attempt {}/{} failed: consumer={}",
                    attempt,
                    resubscribeMaxAttempts,
                    consumerName,
                    e,
                )
                // Back off before every retry — including the last — per the
                // resubscribe policy (1s, 2s, 4s, 8s, 16s for the defaults).
                backoffDelay(backoffMillis)
                backoffMillis *= 2
            }
            attempt++
        }

        isRunning.set(false)
        logger.error(
            "JetStream consumer stopped after {} failed resubscribe attempts; " +
                "health checks will report DOWN: consumer={}, stream={}, subject={}",
            resubscribeMaxAttempts,
            consumerName,
            streamName,
            subjectFilter,
        )
        return false
    }

    private suspend fun CoroutineScope.processMessages(messages: List<Message>) {
        for (message in messages) {
            if (!isActive || !isRunning.get()) {
                break
            }

            handleMessageWithRedeliveryHandling(message)
        }
    }

    private suspend fun handleMessageWithRedeliveryHandling(message: Message) {
        try {
            val jetStreamMessage = convertMessage(message)
            try {
                // Re-open the propagated span context on this thread for the handler via
                // [TraceParent.withRestoredSpan]; a missing or malformed traceparent starts
                // a fresh trace, and the restored span (scope and span itself) is closed
                // right after the handler returns — one message, one entry.
                withRestoredTraceContext(jetStreamMessage.headers[TraceParent.HEADER]?.firstOrNull()) {
                    onMessage(jetStreamMessage)
                    if (autoAck) {
                        message.ack()
                    }
                }
            } catch (handlerEx: Exception) {
                logger.error(
                    "Message handler failed: consumer={}, subject={}, seq={}",
                    consumerName,
                    message.subject,
                    message.metaData()?.streamSequence() ?: 0,
                    handlerEx,
                )
                logTerminalDeliveryIfDropped(message, payloadTypeName(jetStreamMessage), "handler failure")
                try {
                    message.nak()
                } catch (nakEx: Exception) {
                    val totalNakFailures = nakFailureCount.incrementAndGet()
                    logger.error(
                        "Failed to NAK message after handler failure: consumer={}, totalNakFailures={}",
                        consumerName,
                        totalNakFailures,
                        nakEx,
                    )
                }
            }
        } catch (e: Exception) {
            logger.error(
                "Failed to convert message: consumer={}, subject={}, seq={}",
                consumerName,
                message.subject,
                message.metaData()?.streamSequence() ?: 0,
                e,
            )
            logTerminalDeliveryIfDropped(message, "unknown", "conversion failure")
            // Negatively acknowledge to trigger redelivery
            try {
                message.nak()
            } catch (nakEx: Exception) {
                val totalNakFailures = nakFailureCount.incrementAndGet()
                logger.error(
                    "Failed to NAK message after conversion failure: consumer={}, totalNakFailures={}",
                    consumerName,
                    totalNakFailures,
                    nakEx,
                )
            }
        }
    }

    /**
     * Runs [handle] inside [TraceParent.withRestoredSpan] when this consumer carries a
     * traceParent, or plainly when it does not — the handler runs either way, and a
     * missing or malformed header inside the scoped form degrades to a fresh trace.
     */
    private suspend fun withRestoredTraceContext(
        header: String?,
        handle: suspend () -> Unit,
    ) {
        if (traceParent == null) {
            handle()
        } else {
            traceParent.withRestoredSpan(header, handle)
        }
    }

    /**
     * Returns the qualified class name of a message payload, for diagnostics.
     */
    private fun payloadTypeName(jetStreamMessage: JetStreamMessage<T>): String =
        jetStreamMessage.payload?.let { it::class.qualifiedName } ?: "unknown"

    /**
     * Logs at ERROR level when a message that has already reached its final allowed
     * delivery is NAKed after a failure. The server drops such messages silently —
     * no further redelivery — so the drop must be visible in the logs.
     */
    private fun logTerminalDeliveryIfDropped(
        message: Message,
        payloadType: String,
        failure: String,
    ) {
        val metaData = message.metaData() ?: return
        if (!isTerminalDelivery(metaData.deliveredCount(), maxDeliver)) return
        logger.error(
            "Message reached final delivery and will be silently dropped after {}: " +
                "consumer={}, subject={}, streamSequence={}, payloadType={}, deliveredCount={}, maxDeliver={}",
            failure,
            consumerName,
            message.subject,
            metaData.streamSequence(),
            payloadType,
            metaData.deliveredCount(),
            maxDeliver,
        )
    }

    /**
     * Stops consuming messages and cleans up resources.
     * Drains the subscription to cleanly unbind the server-side durable consumer.
     * Resets all state so a subsequent [start] can consume again.
     * Teardown is mutex-protected so a concurrent [resubscribe] cannot create a
     * subscription after the drain, and vice versa. The scope is cancelled only
     * after the drain: an in-flight subscription creation completes, sees
     * `isRunning == false`, and drains its own subscription instead of leaking it.
     */
    fun stopConsuming() {
        logger.info("Stopping message consumption: consumer={}", consumerName)
        isRunning.set(false)
        runBlocking { subscriptionMutex.withLock { teardownSubscriptionLocked("stop") } }
        scope?.cancel()
        scope = null
        consumerStarted.set(false)
    }

    /**
     * Claims the current subscription (nulling [subscriptionRef] atomically) and drains it.
     * MUST be called while holding [subscriptionMutex] so teardown is serialized against
     * [getOrCreateSubscription] and [resubscribe]: at most one live subscription exists
     * at any time and a drained subscription is never reused.
     */
    private suspend fun teardownSubscriptionLocked(reason: String) {
        val oldSubscription = subscriptionRef.getAndSet(null) ?: return
        drainSubscription(oldSubscription, reason)
    }

    /**
     * Drains a subscription off the calling thread, interruptibly and bounded by
     * [RESUBSCRIBE_DRAIN_TIMEOUT]. Non-cancellation failures are logged, not propagated.
     */
    private suspend fun drainSubscription(
        subscription: JetStreamSubscription,
        reason: String,
    ) {
        try {
            runInterruptible(Dispatchers.IO) {
                subscription.drain(RESUBSCRIBE_DRAIN_TIMEOUT)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn("Error draining subscription on {}: consumer={}", reason, consumerName, e)
        }
    }

    /**
     * Converts a NATS message to our domain [JetStreamMessage].
     */
    private fun convertMessage(message: Message): JetStreamMessage<T> {
        val metaData =
            message.metaData()
                ?: throw JetStreamConsumeException("Message metadata is null for subject: ${message.subject}")

        val headers: Map<String, List<String>> =
            message.headers?.let { natsHeaders ->
                natsHeaders.entrySet().associate { item ->
                    item.key to item.value
                }
            } ?: emptyMap()

        // Extract payload and deserialize
        val payload =
            try {
                converter.deserialize(
                    data = message.data,
                    subject = message.subject,
                    headers = headers,
                )
            } catch (e: Exception) {
                throw JetStreamConsumeException(
                    "Failed to deserialize message: subject=${message.subject}, seq=${metaData.streamSequence()}",
                    e,
                )
            }

        // Get message ID from headers or generate one
        val messageIdValue = "$streamName-${metaData.streamSequence()}"

        return JetStreamMessage(
            payload = payload,
            subject = message.subject,
            messageId = messageIdValue,
            sequenceNumber = metaData.streamSequence(),
            timestamp = metaData.timestamp().toInstant(),
            headers = headers,
            deliveryCount = metaData.deliveredCount().toInt(),
            rawMessage = message,
        )
    }

    override suspend fun acknowledge(message: JetStreamMessage<T>) {
        val rawMessage =
            message.rawMessage as? Message
                ?: throw JetStreamConsumeException("Cannot acknowledge message: no raw NATS message reference available")

        try {
            withContext(Dispatchers.IO) {
                rawMessage.ack()
            }
            logger.debug("Message acknowledged: consumer={}, seq={}", consumerName, message.sequenceNumber)
        } catch (e: Exception) {
            throw JetStreamConsumeException(
                "Failed to acknowledge message: consumer=$consumerName, seq=${message.sequenceNumber}",
                e,
            )
        }
    }

    override suspend fun negativeAcknowledge(message: JetStreamMessage<T>) {
        val rawMessage =
            message.rawMessage as? Message
                ?: throw JetStreamConsumeException("Cannot negative acknowledge message: no raw NATS message reference available")

        try {
            withContext(Dispatchers.IO) {
                rawMessage.nak()
            }
            logger.debug("Message negatively acknowledged: consumer={}, seq={}", consumerName, message.sequenceNumber)
        } catch (e: Exception) {
            throw JetStreamConsumeException(
                "Failed to negative acknowledge message: consumer=$consumerName, seq=${message.sequenceNumber}",
                e,
            )
        }
    }
}
