package mz.shared.connector.nats.jetstream.consumer

import io.nats.client.Connection
import io.nats.client.JetStream
import io.nats.client.JetStreamSubscription
import io.nats.client.Message
import io.nats.client.PullSubscribeOptions
import io.nats.client.impl.Headers
import kotlinx.coroutines.CoroutineExceptionHandler
import mz.shared.connector.nats.NatsConnectionFactory
import mz.shared.connector.nats.jetstream.message.JetStreamMessageConverter
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DefaultJetStreamConsumerUnitTest {
    @Mock
    private lateinit var connectionFactory: NatsConnectionFactory

    @Mock
    private lateinit var connection: Connection

    @Mock
    private lateinit var jetStream: JetStream

    @Mock
    private lateinit var subscription: JetStreamSubscription

    @Mock
    private lateinit var converter: JetStreamMessageConverter<String>

    private lateinit var consumer: DefaultJetStreamConsumer<String>

    @BeforeEach
    fun setUp() {
        whenever(connectionFactory.getConnection()).thenReturn(connection)
        whenever(connection.status).thenReturn(Connection.Status.CONNECTED)
        whenever(connection.jetStream()).thenReturn(jetStream)
        whenever(jetStream.subscribe(any<String>(), any<PullSubscribeOptions>())).thenReturn(subscription)
        whenever(subscription.isActive).thenReturn(true)
    }

    @AfterEach
    fun tearDown() {
        if (::consumer.isInitialized) {
            consumer.stop()
        }
    }

    @Test
    fun `should handle connection failure gracefully`() {
        whenever(connectionFactory.getConnection()).thenThrow(RuntimeException("Connection refused"))

        consumer = createConsumer()

        // start() should not throw — the error is caught in the consumption loop
        consumer.start()

        // The consumer should still be "running" (the loop retries with backoff)
        assertThat(consumer.isRunning()).isTrue()
    }

    @Test
    fun `should NAK message when deserialization fails`() {
        val natsMessage = createNatsMessage()
        whenever(subscription.fetch(any<Int>(), any<Duration>()))
            .thenReturn(listOf(natsMessage))
            .thenReturn(emptyList())
        whenever(converter.deserialize(any(), any(), any())).thenThrow(RuntimeException("Bad payload"))

        consumer = createConsumer()
        consumer.start()
        Thread.sleep(200)

        verify(natsMessage).nak()
    }

    @Test
    fun `should continue consumption when NAK itself fails`() {
        val natsMessage = createNatsMessage()
        whenever(subscription.fetch(any<Int>(), any<Duration>()))
            .thenReturn(listOf(natsMessage))
            .thenReturn(emptyList())
        whenever(converter.deserialize(any(), any(), any())).thenThrow(RuntimeException("Bad payload"))
        whenever(natsMessage.nak()).thenThrow(RuntimeException("NAK failed"))

        consumer = createConsumer()
        consumer.start()
        Thread.sleep(300)

        assertThat(consumer.isRunning()).isTrue()
        verify(natsMessage).nak()
    }

    @Test
    fun `should convert NATS headers to domain message headers`() {
        val headers = Headers()
        headers.put("X-Custom-Header", "value1", "value2")
        headers.put("X-Trace-Id", "trace-123")
        val natsMessage = createNatsMessage(headers = headers)
        whenever(subscription.fetch(any<Int>(), any<Duration>()))
            .thenReturn(listOf(natsMessage))
            .thenReturn(emptyList())
        whenever(converter.deserialize(any(), any(), any())).thenReturn("payload")

        val receivedMessages = mutableListOf<JetStreamMessage<String>>()
        consumer = createConsumer(onMessage = { receivedMessages.add(it) })
        consumer.start()
        Thread.sleep(500)

        assertThat(receivedMessages).hasSize(1)
        assertThat(receivedMessages.first().headers)
            .containsEntry("X-Custom-Header", listOf("value1", "value2"))
            .containsEntry("X-Trace-Id", listOf("trace-123"))
    }

    @Test
    fun `should pass empty headers when NATS message has no headers`() {
        val natsMessage = createNatsMessage(headers = null)
        whenever(subscription.fetch(any<Int>(), any<Duration>()))
            .thenReturn(listOf(natsMessage))
            .thenReturn(emptyList())
        whenever(converter.deserialize(any(), any(), any())).thenReturn("payload")

        val receivedMessages = mutableListOf<JetStreamMessage<String>>()
        consumer = createConsumer(onMessage = { receivedMessages.add(it) })
        consumer.start()
        Thread.sleep(500)

        assertThat(receivedMessages).hasSize(1)
        assertThat(receivedMessages.first().headers).isEmpty()
    }

    @Test
    fun `should ACK message when autoAck is enabled and handler succeeds`() {
        val natsMessage = createNatsMessage()
        whenever(subscription.fetch(any<Int>(), any<Duration>()))
            .thenReturn(listOf(natsMessage))
            .thenReturn(emptyList())
        whenever(converter.deserialize(any(), any(), any())).thenReturn("payload")

        consumer = createConsumer(autoAck = true)
        consumer.start()
        Thread.sleep(200)

        verify(natsMessage).ack()
    }

    @Test
    fun `should NAK message when autoAck is enabled and handler throws`() {
        val natsMessage = createNatsMessage()
        whenever(subscription.fetch(any<Int>(), any<Duration>()))
            .thenReturn(listOf(natsMessage))
            .thenReturn(emptyList())
        whenever(converter.deserialize(any(), any(), any())).thenReturn("payload")

        consumer = createConsumer(autoAck = true, onMessage = { throw RuntimeException("Handler failed") })
        consumer.start()
        Thread.sleep(200)

        verify(natsMessage).nak()
    }

    @Test
    fun `should set isRunning to false when consume loop fails`() {
        whenever(subscription.fetch(any<Int>(), any<Duration>())).thenThrow(AssertionError("fetch boom"))

        consumer = createConsumer()
        consumer.start()

        await.atMost(Duration.ofSeconds(5)).until { !consumer.isRunning() }
    }

    @Test
    fun `should throw JetStreamConsumeException when connection is not CONNECTED after gate timeout`() {
        whenever(connection.status).thenReturn(Connection.Status.RECONNECTING)

        val capturedExceptions = CopyOnWriteArrayList<Throwable>()
        val testHandler = CoroutineExceptionHandler { _, t -> capturedExceptions.add(t) }

        consumer =
            createConsumer(
                connectedGateTimeoutMs = 1_000L,
                connectedGatePollIntervalMs = 100L,
                coroutineExceptionHandler = testHandler,
            )

        consumer.start()

        await.atMost(Duration.ofSeconds(5)).until { capturedExceptions.isNotEmpty() }

        assertThat(consumer.isRunning()).isFalse()
        val ex = capturedExceptions.first()
        assertThat(ex).isInstanceOf(JetStreamConsumeException::class.java)
        assertThat(ex.message)
            .contains("Connection not CONNECTED")
            .contains("test-consumer")
            .contains("test-stream")
            .contains("RECONNECTING")
    }

    @Test
    fun `should subscribe when connection becomes CONNECTED after initial RECONNECTING`() {
        whenever(connection.status)
            .thenReturn(Connection.Status.RECONNECTING)
            .thenReturn(Connection.Status.CONNECTED)

        val receivedMessages = mutableListOf<JetStreamMessage<String>>()
        whenever(subscription.fetch(any<Int>(), any<Duration>()))
            .thenReturn(emptyList())

        consumer =
            createConsumer(
                onMessage = { receivedMessages.add(it) },
                connectedGateTimeoutMs = 5_000L,
                connectedGatePollIntervalMs = 50L,
            )
        consumer.start()

        // If we get here without exception, subscription was created successfully
        assertThat(consumer.isRunning()).isTrue()
    }

    @Test
    fun `should subscribe immediately when connection is already CONNECTED`() {
        // connection.status is already CONNECTED from setUp()
        whenever(subscription.fetch(any<Int>(), any<Duration>()))
            .thenReturn(emptyList())

        consumer = createConsumer()
        consumer.start()

        assertThat(consumer.isRunning()).isTrue()
    }

    @Test
    fun `should throw JetStreamConsumeException when subscribe times out`() {
        // Make subscribe block until interrupted — the short timeout will fire first
        val latch = java.util.concurrent.CountDownLatch(1)
        whenever(jetStream.subscribe(any<String>(), any<PullSubscribeOptions>())).thenAnswer {
            try {
                latch.await() // blocks until interrupted
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
            throw InterruptedException("interrupted by timeout")
        }

        val capturedExceptions = CopyOnWriteArrayList<Throwable>()
        val testHandler = CoroutineExceptionHandler { _, t -> capturedExceptions.add(t) }

        consumer =
            createConsumer(
                subscribeTimeoutMs = 200L,
                coroutineExceptionHandler = testHandler,
            )

        consumer.start()

        await.atMost(Duration.ofSeconds(5)).until { capturedExceptions.isNotEmpty() }

        assertThat(consumer.isRunning()).isFalse()
        val ex = capturedExceptions.first()
        assertThat(ex).isInstanceOf(JetStreamConsumeException::class.java)
        assertThat(ex.message)
            .contains("Timed out creating JetStream subscription")
            .contains("test.>")
            .contains("test-consumer")
            .contains("test-stream")
    }

    @Test
    fun `should restart consuming when start is called after stop`() {
        whenever(subscription.fetch(any<Int>(), any<Duration>())).thenReturn(emptyList())

        consumer = createConsumer()
        consumer.start()

        await.atMost(Duration.ofSeconds(5)).untilAsserted {
            verify(jetStream, times(1)).subscribe(any<String>(), any<PullSubscribeOptions>())
        }

        consumer.stop()
        consumer.start()

        await.atMost(Duration.ofSeconds(5)).untilAsserted {
            verify(jetStream, times(2)).subscribe(any<String>(), any<PullSubscribeOptions>())
        }
        assertThat(consumer.isRunning()).isTrue()
    }

    @Test
    fun `should recreate subscription after consecutive fetch failures`() {
        val natsMessage = createNatsMessage()
        whenever(subscription.fetch(any<Int>(), any<Duration>()))
            .thenThrow(RuntimeException("fetch failed"))
        whenever(converter.deserialize(any(), any(), any())).thenReturn("payload")

        val secondSubscription = mock<JetStreamSubscription>()
        whenever(secondSubscription.isActive).thenReturn(true)
        whenever(secondSubscription.fetch(any<Int>(), any<Duration>()))
            .thenReturn(listOf(natsMessage))
            .thenReturn(emptyList())
        whenever(jetStream.subscribe(any<String>(), any<PullSubscribeOptions>()))
            .thenReturn(subscription)
            .thenReturn(secondSubscription)

        val receivedMessages = CopyOnWriteArrayList<JetStreamMessage<String>>()
        consumer = createConsumer(onMessage = { receivedMessages.add(it) })
        consumer.start()

        await.atMost(Duration.ofSeconds(10)).until { receivedMessages.isNotEmpty() }

        verify(subscription).drain(any<Duration>())
        verify(jetStream, times(2)).subscribe(any<String>(), any<PullSubscribeOptions>())
    }

    @Test
    fun `should not hold two live subscriptions during resubscribe`() {
        whenever(subscription.fetch(any<Int>(), any<Duration>()))
            .thenThrow(RuntimeException("fetch failed"))
        whenever(converter.deserialize(any(), any(), any())).thenReturn("payload")

        val secondSubscription = mock<JetStreamSubscription>()
        whenever(secondSubscription.isActive).thenReturn(true)
        whenever(secondSubscription.fetch(any<Int>(), any<Duration>())).thenReturn(emptyList())
        whenever(jetStream.subscribe(any<String>(), any<PullSubscribeOptions>()))
            .thenReturn(subscription)
            .thenReturn(secondSubscription)

        consumer = createConsumer()
        consumer.start()

        await.atMost(Duration.ofSeconds(10)).untilAsserted {
            val inOrder = inOrder(subscription, jetStream)
            inOrder.verify(subscription).drain(any<Duration>())
            inOrder.verify(jetStream).subscribe(any<String>(), any<PullSubscribeOptions>())
        }
        assertThat(consumer.isRunning()).isTrue()
    }

    @Test
    fun `should stop consumer and mark not running after resubscribe attempts are exhausted`() {
        whenever(subscription.fetch(any<Int>(), any<Duration>()))
            .thenThrow(RuntimeException("fetch failed"))
        whenever(jetStream.subscribe(any<String>(), any<PullSubscribeOptions>()))
            .thenReturn(subscription)
            .thenThrow(RuntimeException("subscribe failed"))

        consumer =
            createConsumer(
                resubscribeMaxAttempts = 3,
                resubscribeBackoffMillis = 10L,
            )
        consumer.start()

        await.atMost(Duration.ofSeconds(10)).until { !consumer.isRunning() }
        // 1 initial subscription + 3 resubscribe attempts, then the consumer stops
        verify(jetStream, times(4)).subscribe(any<String>(), any<PullSubscribeOptions>())
    }

    @Test
    fun `should log error when handler fails on final delivery`() {
        val natsMessage = createNatsMessage(deliveredCount = 3)
        whenever(subscription.fetch(any<Int>(), any<Duration>()))
            .thenReturn(listOf(natsMessage))
            .thenReturn(emptyList())
        whenever(converter.deserialize(any(), any(), any())).thenReturn("payload")

        val logEvents =
            withCapturedLogs {
                consumer = createConsumer(onMessage = { throw RuntimeException("Handler failed") })
                consumer.start()
                Thread.sleep(300)
            }

        val terminalErrors =
            logEvents.filter { event ->
                event.level == ch.qos.logback.classic.Level.ERROR &&
                    event.formattedMessage.contains("final delivery")
            }
        assertThat(terminalErrors).hasSize(1)
        assertThat(terminalErrors.first().formattedMessage)
            .contains("consumer=test-consumer")
            .contains("subject=test.subject")
            .contains("streamSequence=1")
            .contains("payloadType=kotlin.String")
    }

    @Test
    fun `should not log terminal error before final delivery`() {
        val natsMessage = createNatsMessage(deliveredCount = 1)
        whenever(subscription.fetch(any<Int>(), any<Duration>()))
            .thenReturn(listOf(natsMessage))
            .thenReturn(emptyList())
        whenever(converter.deserialize(any(), any(), any())).thenReturn("payload")

        val logEvents =
            withCapturedLogs {
                consumer = createConsumer(onMessage = { throw RuntimeException("Handler failed") })
                consumer.start()
                Thread.sleep(300)
            }

        val terminalErrors =
            logEvents.filter { event ->
                event.level == ch.qos.logback.classic.Level.ERROR &&
                    event.formattedMessage.contains("final delivery")
            }
        assertThat(terminalErrors).isEmpty()
        // The message is still NAKed for redelivery
        verify(natsMessage).nak()
    }

    @Test
    fun `should detect terminal delivery when delivered count reaches max deliver`() {
        assertThat(isTerminalDelivery(deliveredCount = 3, maxDeliver = 3)).isTrue()
    }

    @Test
    fun `should not detect terminal delivery before final delivery`() {
        assertThat(isTerminalDelivery(deliveredCount = 1, maxDeliver = 3)).isFalse()
    }

    @Test
    fun `should log error when message fails deserialization on final delivery`() {
        val natsMessage = createNatsMessage(deliveredCount = 3)
        whenever(subscription.fetch(any<Int>(), any<Duration>()))
            .thenReturn(listOf(natsMessage))
            .thenReturn(emptyList())
        whenever(converter.deserialize(any(), any(), any())).thenThrow(RuntimeException("Bad payload"))

        val logEvents =
            withCapturedLogs {
                consumer = createConsumer()
                consumer.start()
                Thread.sleep(300)
            }

        val terminalErrors =
            logEvents.filter { event ->
                event.level == ch.qos.logback.classic.Level.ERROR &&
                    event.formattedMessage.contains("final delivery")
            }
        assertThat(terminalErrors).hasSize(1)
        assertThat(terminalErrors.first().formattedMessage)
            .contains("consumer=test-consumer")
            .contains("subject=test.subject")
            .contains("streamSequence=1")
            .contains("payloadType=unknown")
            .contains("after conversion failure")
    }

    private fun withCapturedLogs(block: () -> Unit): List<ch.qos.logback.classic.spi.ILoggingEvent> {
        val logbackLogger = LoggerFactory.getLogger(DefaultJetStreamConsumer::class.java) as ch.qos.logback.classic.Logger
        val appender =
            ch.qos.logback.core.read
                .ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>()
        appender.start()
        logbackLogger.addAppender(appender)
        try {
            block()
            return appender.list.toList()
        } finally {
            logbackLogger.detachAppender(appender)
        }
    }

    @Test
    fun `should apply exponential backoff before every resubscribe attempt`() {
        whenever(subscription.fetch(any<Int>(), any<Duration>()))
            .thenThrow(RuntimeException("fetch failed"))
        whenever(jetStream.subscribe(any<String>(), any<PullSubscribeOptions>()))
            .thenReturn(subscription)
            .thenThrow(RuntimeException("subscribe failed"))

        val recordedDelays = CopyOnWriteArrayList<Long>()
        consumer =
            createConsumer(
                resubscribeMaxAttempts = 5,
                resubscribeBackoffMillis = 1_000L,
                backoffDelay = { millis -> recordedDelays.add(millis) },
            )
        consumer.start()

        await.atMost(Duration.ofSeconds(10)).until { !consumer.isRunning() }
        assertThat(recordedDelays).containsExactly(1_000L, 2_000L, 4_000L, 8_000L, 16_000L)
    }

    @Test
    fun `should attempt subscribe again when start is called after a fatal creation failure`() {
        whenever(jetStream.subscribe(any<String>(), any<PullSubscribeOptions>()))
            .thenThrow(RuntimeException("subscribe failed"))
            .thenReturn(subscription)
        whenever(subscription.fetch(any<Int>(), any<Duration>())).thenReturn(emptyList())

        consumer = createConsumer()
        consumer.start()

        await.atMost(Duration.ofSeconds(5)).until { !consumer.isRunning() }
        verify(jetStream, times(1)).subscribe(any<String>(), any<PullSubscribeOptions>())

        consumer.start()

        await.atMost(Duration.ofSeconds(5)).untilAsserted {
            verify(jetStream, times(2)).subscribe(any<String>(), any<PullSubscribeOptions>())
        }
        assertThat(consumer.isRunning()).isTrue()
    }

    @Test
    fun `should drain subscription created during stop instead of leaking it`() {
        val secondSubscription = mock<JetStreamSubscription>()
        whenever(secondSubscription.isActive).thenReturn(true)
        val subscribeStarted = java.util.concurrent.CountDownLatch(1)
        val releaseSubscribe = java.util.concurrent.CountDownLatch(1)
        whenever(subscription.fetch(any<Int>(), any<Duration>()))
            .thenThrow(RuntimeException("fetch failed"))
        whenever(jetStream.subscribe(any<String>(), any<PullSubscribeOptions>()))
            .thenReturn(subscription)
            .thenAnswer {
                subscribeStarted.countDown()
                var released = false
                while (!released) {
                    released =
                        try {
                            releaseSubscribe.await(50, java.util.concurrent.TimeUnit.MILLISECONDS)
                        } catch (_: InterruptedException) {
                            // Ignore interrupts so the subscribe stays in flight while stop() runs
                            false
                        }
                }
                secondSubscription
            }

        consumer =
            createConsumer(
                resubscribeBackoffMillis = 10L,
            )
        consumer.start()

        assertThat(subscribeStarted.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue()

        val stopThread = kotlin.concurrent.thread { consumer.stop() }
        Thread.sleep(200)
        releaseSubscribe.countDown()
        stopThread.join(
            java.util.concurrent.TimeUnit.SECONDS
                .toMillis(10),
        )

        // At most one resubscribe attempt: stop prevents further work
        await.atMost(Duration.ofSeconds(10)).untilAsserted {
            verify(jetStream, times(2)).subscribe(any<String>(), any<PullSubscribeOptions>())
        }
        // The subscription created while stopping must be drained, not leaked
        await.atMost(Duration.ofSeconds(10)).untilAsserted {
            verify(secondSubscription).drain(any<Duration>())
        }
        val inOrder = inOrder(subscription, jetStream, secondSubscription)
        inOrder.verify(subscription).drain(any<Duration>())
        inOrder.verify(jetStream).subscribe(any<String>(), any<PullSubscribeOptions>())
        inOrder.verify(secondSubscription).drain(any<Duration>())
    }

    private fun createConsumer(
        autoAck: Boolean = false,
        onMessage: suspend (JetStreamMessage<String>) -> Unit = { },
        connectedGateTimeoutMs: Long = 15_000L,
        connectedGatePollIntervalMs: Long = 500L,
        subscribeTimeoutMs: Long = 15_000L,
        resubscribeMaxAttempts: Int = 5,
        resubscribeBackoffMillis: Long = 1_000L,
        backoffDelay: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
        coroutineExceptionHandler: CoroutineExceptionHandler =
            CoroutineExceptionHandler { _, _ -> },
    ): DefaultJetStreamConsumer<String> =
        DefaultJetStreamConsumer(
            consumerName = "test-consumer",
            streamName = "test-stream",
            subjectFilter = "test.>",
            connectionFactory = connectionFactory,
            converter = converter,
            onMessage = onMessage,
            autoAck = autoAck,
            fetchIntervalMillis = 50L,
            connectedGateTimeoutMs = connectedGateTimeoutMs,
            connectedGatePollIntervalMs = connectedGatePollIntervalMs,
            subscribeTimeoutMs = subscribeTimeoutMs,
            resubscribeMaxAttempts = resubscribeMaxAttempts,
            resubscribeBackoffMillis = resubscribeBackoffMillis,
            backoffDelay = backoffDelay,
            coroutineExceptionHandler = coroutineExceptionHandler,
        )

    @Suppress("UNCHECKED_CAST")
    private fun createNatsMessage(
        headers: Headers? = null,
        deliveredCount: Long = 1,
    ): Message {
        val message = mock<Message>()
        whenever(message.data).thenReturn("test".toByteArray())
        whenever(message.subject).thenReturn("test.subject")
        whenever(message.headers).thenReturn(headers)
        val metaData = mock<io.nats.client.impl.NatsJetStreamMetaData>()
        whenever(metaData.streamSequence()).thenReturn(1L)
        whenever(metaData.timestamp()).thenReturn(java.time.ZonedDateTime.now())
        whenever(metaData.deliveredCount()).thenReturn(deliveredCount)
        whenever(message.metaData()).thenReturn(metaData)
        return message
    }
}
