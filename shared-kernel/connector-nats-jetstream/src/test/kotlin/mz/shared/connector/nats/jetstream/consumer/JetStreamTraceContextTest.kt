package mz.shared.connector.nats.jetstream.consumer

import io.micrometer.tracing.test.simple.SimpleSpan
import io.micrometer.tracing.test.simple.SimpleTracer
import io.nats.client.Connection
import io.nats.client.JetStream
import io.nats.client.api.StorageType
import io.nats.client.api.StreamConfiguration
import io.nats.client.impl.Headers
import kotlinx.coroutines.runBlocking
import mz.shared.connector.nats.DefaultNatsConnectionFactory
import mz.shared.connector.nats.NatsConnectionFactory
import mz.shared.connector.nats.NatsConnectionProperties
import mz.shared.connector.nats.jetstream.message.JetStreamMessageConverter
import mz.shared.connector.nats.jetstream.producer.DefaultJetStreamProducer
import mz.shared.connector.nats.jetstream.producer.JetStreamPublishRequest
import mz.shared.connector.observability.SimpleW3CPropagator
import mz.shared.connector.observability.TraceParent
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList

private const val TRACE_ID = "0af7651916cd43dd8448eb211c80319c"
private const val SPAN_ID = "b7ad6b7169203331"
private const val HEADER_VALUE = "00-$TRACE_ID-$SPAN_ID-01"

/**
 * JetStream hop of the W3C traceparent propagation: the header survives the transport
 * verbatim and the consumer restores the span context before the handler runs. A message
 * without the header still processes with a fresh trace.
 */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JetStreamTraceContextTest {
    @Container
    private val natsContainer: GenericContainer<*> =
        GenericContainer("nats:2.10.7")
            .withExposedPorts(4222)
            .withCommand("--config", "/config/jetstream.conf")
            .withClasspathResourceMapping("nats-jetstream.conf", "/config/jetstream.conf", BindMode.READ_ONLY)
            .waitingFor(Wait.forLogMessage(".*Server is ready.*", 1))

    private val streamName = "TRACE_STREAM"
    private val subjectFilter = "trace.subject.>"

    // Factories created per test — tracked for proper cleanup
    private val openFactories = CopyOnWriteArrayList<DefaultNatsConnectionFactory>()

    @AfterEach
    fun closeFactories() {
        openFactories.forEach { factory ->
            runCatching { factory.close() }
        }
        openFactories.clear()
    }

    @Test
    fun `round trips the traceparent header from the publish request to the consumed message`() {
        val receivedMessages = CopyOnWriteArrayList<JetStreamMessage<String>>()
        val connection = createConnection()
        createStream(connection)

        val producer =
            DefaultJetStreamProducer(
                streamName = streamName,
                connectionFactory = createConnectionFactory(),
                converter = StringConverter(),
            )

        runBlocking {
            producer.publish(
                JetStreamPublishRequest(
                    subject = "trace.subject.one",
                    payload = "trace-header-payload",
                    headers = mapOf(TraceParent.HEADER to HEADER_VALUE),
                ),
            )
        }

        val consumer = newConsumer(consumerName = "trace-round-trip-consumer") { receivedMessages.add(it) }
        consumer.start()

        await.atMost(Duration.ofSeconds(10)).until { receivedMessages.isNotEmpty() }
        (consumer as? DefaultJetStreamConsumer)?.stopConsuming()

        assertThat(receivedMessages).hasSize(1)
        assertThat(receivedMessages[0].headers[TraceParent.HEADER]).containsExactly(HEADER_VALUE)
        assertThat(receivedMessages[0].payload).isEqualTo("trace-header-payload")
    }

    @Test
    fun `consumer restores the traceparent span context before the message handler runs`() {
        val receivedMessages = CopyOnWriteArrayList<JetStreamMessage<String>>()
        val observedTraceIds = CopyOnWriteArrayList<String?>()
        val observedSpans = CopyOnWriteArrayList<SimpleSpan?>()
        val tracer = SimpleTracer()
        val connection = createConnection()
        createStream(connection)

        val consumer =
            newConsumer(
                consumerName = "trace-restore-consumer",
                traceParent = TraceParent(tracer, SimpleW3CPropagator(tracer)),
            ) { message ->
                observedTraceIds.add(tracer.currentSpan()?.context()?.traceId())
                observedSpans.add(tracer.currentSpan() as? SimpleSpan)
                receivedMessages.add(message)
            }
        consumer.start()

        publish(connection, "trace.subject.two", "restored-payload", traceparent = HEADER_VALUE)

        await.atMost(Duration.ofSeconds(10)).until { receivedMessages.isNotEmpty() }
        (consumer as? DefaultJetStreamConsumer)?.stopConsuming()

        assertThat(observedTraceIds).containsExactly(TRACE_ID)
        assertThat(observedSpans.first()?.endTimestamp?.toEpochMilli()).isGreaterThan(0)
        assertThat(receivedMessages[0].payload).isEqualTo("restored-payload")
    }

    @Test
    fun `consumer processes a message without a traceparent header with a fresh trace`() {
        val receivedMessages = CopyOnWriteArrayList<JetStreamMessage<String>>()
        val observedTraceIds = CopyOnWriteArrayList<String?>()
        val tracer = SimpleTracer()
        val connection = createConnection()
        createStream(connection)

        val consumer =
            newConsumer(
                consumerName = "trace-fresh-consumer",
                traceParent = TraceParent(tracer, SimpleW3CPropagator(tracer)),
            ) { message ->
                observedTraceIds.add(tracer.currentSpan()?.context()?.traceId())
                receivedMessages.add(message)
            }
        consumer.start()

        publish(connection, "trace.subject.three", "fresh-payload")

        await.atMost(Duration.ofSeconds(10)).until { receivedMessages.isNotEmpty() }
        (consumer as? DefaultJetStreamConsumer)?.stopConsuming()

        assertThat(observedTraceIds).containsExactly(null as String?)
        assertThat(receivedMessages[0].payload).isEqualTo("fresh-payload")
    }

    private fun getNatsUrl(): String = "nats://${natsContainer.host}:${natsContainer.firstMappedPort}"

    private fun createConnectionFactory(): NatsConnectionFactory {
        val properties = NatsConnectionProperties(serverUrl = getNatsUrl())
        return DefaultNatsConnectionFactory(properties).also { openFactories.add(it) }
    }

    private fun createConnection(): Connection = createConnectionFactory().getConnection()

    private fun createStream(connection: Connection) {
        val jsm = connection.jetStreamManagement()

        try {
            jsm.deleteStream(streamName)
        } catch (_: Exception) {
        }

        val config =
            StreamConfiguration
                .builder()
                .name(streamName)
                .subjects("trace.subject.*")
                .storageType(StorageType.Memory)
                .build()

        jsm.addStream(config)
    }

    private fun publish(
        connection: Connection,
        subject: String,
        payload: String,
        traceparent: String? = null,
    ) {
        val jetStream: JetStream = connection.jetStream()
        val data = payload.toByteArray(StandardCharsets.UTF_8)
        if (traceparent == null) {
            jetStream.publish(subject, data)
        } else {
            jetStream.publish(subject, Headers().add(TraceParent.HEADER, traceparent), data)
        }
    }

    private fun newConsumer(
        consumerName: String,
        traceParent: TraceParent? = null,
        handler: suspend (JetStreamMessage<String>) -> Unit,
    ): JetStreamConsumer<String> =
        JetStreamConsumerBuilder<String>(
            streamName = streamName,
            subject = subjectFilter,
            consumerName = consumerName,
        ).withConnectionFactory(createConnectionFactory())
            .withConverter(StringConverter())
            .withTraceParent(traceParent)
            .withMessageHandler(handler)
            .withBatchSize(10)
            .build()

    private class StringConverter : JetStreamMessageConverter<String> {
        override fun serialize(payload: String): ByteArray = payload.toByteArray(StandardCharsets.UTF_8)

        override fun deserialize(
            data: ByteArray,
            subject: String,
            headers: Map<String, List<String>>,
        ): String = String(data, StandardCharsets.UTF_8)

        override val contentType: String = "text/plain"
    }
}
