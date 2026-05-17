package mz.shared.connector.nats.jetstream.consumer

import io.nats.client.JetStream
import io.nats.client.JetStreamSubscription
import io.nats.client.Message
import io.nats.client.PullSubscribeOptions
import io.nats.client.api.ConsumerConfiguration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mz.shared.connector.nats.NatsConnectionFactory
import mz.shared.connector.nats.jetstream.message.JetStreamMessageConverter
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
) : JetStreamConsumer<T>,
    SmartLifecycle {
    private val logger = LoggerFactory.getLogger(DefaultJetStreamConsumer::class.java)

    companion object {
        private const val ERROR_BACKOFF_MULTIPLIER = 5L
    }

    // Holds the subscription reference once initialized; null until first use
    private val subscriptionRef = AtomicReference<JetStreamSubscription?>(null)

    // Lazily initializes the subscription and stores it in subscriptionRef for cleanup
    private val subscription: JetStreamSubscription by lazy {
        createSubscription().also { subscriptionRef.set(it) }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
        logger.info("JetStream consumer subscription created successfully: consumer={}", consumerName)
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
     */
    private fun createSubscription(): JetStreamSubscription =
        try {
            val connection = connectionFactory.getConnection()
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

            // Create pull subscription
            val sub = jetStream.subscribe(subjectFilter, pullOptions)

            // Ensure connection is flushed
            connection.flush(Duration.ofSeconds(1))

            logger.info(
                "Created JetStream pull consumer: consumer={}, stream={}, subject={}, durable={}",
                consumerName,
                streamName,
                subjectFilter,
                isDurable,
            )

            sub
        } catch (e: Exception) {
            throw JetStreamConsumeException(
                "Failed to create JetStream consumer subscription: consumer=$consumerName, stream=$streamName, subject=$subjectFilter",
                e,
            )
        }

    /**
     * Starts consuming messages in a background coroutine.
     * This is called automatically during initialization.
     */
    private fun startConsuming() {
        scope.launch {
            logger.debug("Starting message consumption: consumer={}", consumerName)

            // Ensure subscription is initialized
            val sub = subscription

            while (isActive && isRunning.get()) {
                try {
                    // Fetch messages from JetStream
                    val messages =
                        withContext(Dispatchers.IO) {
                            sub.fetch(batchSize, Duration.ofMillis(maxWaitMillis))
                        }.takeIf { it != null }
                            ?: emptyList()

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
                    logger.error("Error during message consumption: consumer={}", consumerName, e)
                    // Brief delay before retry to avoid tight error loops
                    delay(fetchIntervalMillis * ERROR_BACKOFF_MULTIPLIER)
                }
            }
        }
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
                onMessage(jetStreamMessage)
                if (autoAck) {
                    message.ack()
                }
            } catch (handlerEx: Exception) {
                logger.error(
                    "Message handler failed: consumer={}, subject={}, seq={}",
                    consumerName,
                    message.subject,
                    message.metaData()?.streamSequence() ?: 0,
                    handlerEx,
                )
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
     * Stops consuming messages and cleans up resources.
     * Drains the subscription to cleanly unbind the server-side durable consumer.
     */
    fun stopConsuming() {
        logger.info("Stopping message consumption: consumer={}", consumerName)
        isRunning.set(false)
        scope.cancel()
        subscriptionRef.get()?.let { sub ->
            runCatching { sub.drain(Duration.ofSeconds(5)) }
                .onFailure { logger.warn("Error draining subscription on stop: consumer={}", consumerName, it) }
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
