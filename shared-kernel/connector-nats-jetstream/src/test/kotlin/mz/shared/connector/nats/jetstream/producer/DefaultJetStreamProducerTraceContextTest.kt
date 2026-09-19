package mz.shared.connector.nats.jetstream.producer

import io.micrometer.tracing.test.simple.SimpleSpan
import io.micrometer.tracing.test.simple.SimpleTracer
import io.nats.client.Connection
import io.nats.client.JetStream
import io.nats.client.api.PublishAck
import io.nats.client.impl.Headers
import io.nats.client.impl.NatsMessage
import kotlinx.coroutines.runBlocking
import mz.shared.connector.nats.NatsConnectionFactory
import mz.shared.connector.nats.jetstream.message.JetStreamMessageConverter
import mz.shared.connector.observability.SimpleW3CPropagator
import mz.shared.connector.observability.TraceParent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

private const val TRACE_ID = "0af7651916cd43dd8448eb211c80319c"
private const val SPAN_ID = "b7ad6b7169203331"

/** Real JetStream pub-ack JSON, as the server sends it. */
private const val PUB_ACK_JSON = """{"type":"io.nats.jetstream.api.v1.pub_ack_response","stream":"STREAM","seq":1}"""

private class StringConverter : JetStreamMessageConverter<String> {
    override fun serialize(payload: String): ByteArray = payload.toByteArray(Charsets.UTF_8)

    override fun deserialize(
        data: ByteArray,
        subject: String,
        headers: Map<String, List<String>>,
    ): String = String(data, Charsets.UTF_8)

    override val contentType: String = "text/plain"
}

/**
 * Producer side of the W3C traceparent propagation: when a [TraceParent] is wired in,
 * the current span is stamped as a `traceparent` NATS header on outbound messages;
 * without an active span (or without a [TraceParent]) no header is stamped, and a
 * caller-supplied header is never overwritten.
 */
@ExtendWith(MockitoExtension::class)
class DefaultJetStreamProducerTraceContextTest {
    @Mock
    private lateinit var connectionFactory: NatsConnectionFactory

    @Mock
    private lateinit var connection: Connection

    @Mock
    private lateinit var jetStream: JetStream

    private val tracer = SimpleTracer()
    private val converter = StringConverter()

    /** A real [PublishAck], parsed exactly like a server-side one; no mock on [PublishAck]. */
    private fun publishAck(): PublishAck = PublishAck(NatsMessage("subject", null, PUB_ACK_JSON.toByteArray(Charsets.UTF_8)))

    private fun stubJetStreamPublish() {
        whenever(connectionFactory.getConnection()).thenReturn(connection)
        whenever(connection.jetStream()).thenReturn(jetStream)
        whenever(jetStream.publish(any<String>(), any<Headers>(), any<ByteArray>())).thenReturn(publishAck())
    }

    private fun createProducer(traceParent: TraceParent?): DefaultJetStreamProducer<String> =
        DefaultJetStreamProducer(
            streamName = "STREAM",
            connectionFactory = connectionFactory,
            converter = converter,
            traceParent = traceParent,
        )

    private fun capturedHeaders(): Headers {
        val captor: ArgumentCaptor<Headers> = ArgumentCaptor.forClass(Headers::class.java)
        verify(jetStream).publish(any<String>(), captor.capture(), any<ByteArray>())
        return captor.value
    }

    @Test
    fun `stamps the traceparent header from the current span on publish`() {
        runBlocking {
            stubJetStreamPublish()

            val span =
                SimpleSpan().apply {
                    context().setTraceId(TRACE_ID)
                    context().setSpanId(SPAN_ID)
                    context().setSampled(true)
                    start()
                }
            val producer = createProducer(TraceParent(tracer, SimpleW3CPropagator(tracer)))

            tracer.withSpan(span).use {
                producer.publish(JetStreamPublishRequest(subject = "subject.one", payload = "payload"))
            }

            assertThat(capturedHeaders().getFirst(TraceParent.HEADER))
                .isEqualTo("00-$TRACE_ID-$SPAN_ID-01")
        }
    }

    @Test
    fun `stamps the traceparent header on each message of a batch`() {
        runBlocking {
            stubJetStreamPublish()

            val span =
                SimpleSpan().apply {
                    context().setTraceId(TRACE_ID)
                    context().setSpanId(SPAN_ID)
                    context().setSampled(true)
                    start()
                }
            val producer = createProducer(TraceParent(tracer, SimpleW3CPropagator(tracer)))

            tracer.withSpan(span).use {
                producer.publishBatch(
                    listOf(
                        JetStreamPublishRequest(subject = "subject.one", payload = "payload"),
                        JetStreamPublishRequest(subject = "subject.two", payload = "payload"),
                    ),
                )
            }

            val captor: ArgumentCaptor<Headers> = ArgumentCaptor.forClass(Headers::class.java)
            verify(jetStream, times(2)).publish(any<String>(), captor.capture(), any<ByteArray>())
            val traceparentHeaders = captor.allValues.map { it.getFirst(TraceParent.HEADER) }
            assertThat(traceparentHeaders)
                .containsExactly("00-$TRACE_ID-$SPAN_ID-01", "00-$TRACE_ID-$SPAN_ID-01")
        }
    }

    @Test
    fun `stamps no traceparent header when traceParent is null`() {
        runBlocking {
            stubJetStreamPublish()

            val producer = createProducer(traceParent = null)
            producer.publish(JetStreamPublishRequest(subject = "subject.one", payload = "payload"))

            assertThat(capturedHeaders().getFirst(TraceParent.HEADER)).isNull()
        }
    }

    @Test
    fun `does not overwrite a caller supplied traceparent header`() {
        runBlocking {
            stubJetStreamPublish()

            val span =
                SimpleSpan().apply {
                    context().setTraceId(TRACE_ID)
                    context().setSpanId(SPAN_ID)
                    context().setSampled(true)
                    start()
                }
            val producer = createProducer(TraceParent(tracer, SimpleW3CPropagator(tracer)))

            tracer.withSpan(span).use {
                producer.publish(
                    JetStreamPublishRequest(
                        subject = "subject.one",
                        payload = "payload",
                        headers = mapOf(TraceParent.HEADER to "00-caller-supplied"),
                    ),
                )
            }

            assertThat(capturedHeaders().getFirst(TraceParent.HEADER)).isEqualTo("00-caller-supplied")
        }
    }
}
