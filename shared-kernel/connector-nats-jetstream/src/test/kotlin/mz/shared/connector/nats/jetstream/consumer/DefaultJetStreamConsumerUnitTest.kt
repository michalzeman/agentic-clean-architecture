package mz.shared.connector.nats.jetstream.consumer

import io.nats.client.Connection
import io.nats.client.JetStream
import io.nats.client.JetStreamSubscription
import io.nats.client.Message
import io.nats.client.PullSubscribeOptions
import io.nats.client.impl.Headers
import mz.shared.connector.nats.NatsConnectionFactory
import mz.shared.connector.nats.jetstream.message.JetStreamMessageConverter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.time.Duration

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
        whenever(connection.jetStream()).thenReturn(jetStream)
        whenever(jetStream.subscribe(any<String>(), any<PullSubscribeOptions>())).thenReturn(subscription)
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

    private fun createConsumer(
        autoAck: Boolean = false,
        onMessage: suspend (JetStreamMessage<String>) -> Unit = { },
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
        )

    @Suppress("UNCHECKED_CAST")
    private fun createNatsMessage(headers: Headers? = null): Message {
        val message = mock<Message>()
        whenever(message.data).thenReturn("test".toByteArray())
        whenever(message.subject).thenReturn("test.subject")
        whenever(message.headers).thenReturn(headers)
        val metaData = mock<io.nats.client.impl.NatsJetStreamMetaData>()
        whenever(metaData.streamSequence()).thenReturn(1L)
        whenever(metaData.timestamp()).thenReturn(java.time.ZonedDateTime.now())
        whenever(metaData.deliveredCount()).thenReturn(1)
        whenever(message.metaData()).thenReturn(metaData)
        return message
    }
}
