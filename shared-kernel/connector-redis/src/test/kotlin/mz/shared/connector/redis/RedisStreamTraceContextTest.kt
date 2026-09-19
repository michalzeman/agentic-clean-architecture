package mz.shared.connector.redis

import com.google.protobuf.StringValue
import com.hubspot.jackson3.datatype.protobuf.ProtobufModule
import io.micrometer.tracing.test.simple.SimpleSpan
import io.micrometer.tracing.test.simple.SimpleTracer
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import mz.shared.connector.observability.SimpleW3CPropagator
import mz.shared.connector.observability.TraceParent
import mz.shared.connector.redis.json.RedisMapRecordJsonSerializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.data.redis.connection.stream.Consumer
import org.springframework.data.redis.connection.stream.MapRecord
import org.springframework.data.redis.connection.stream.Record
import org.springframework.data.redis.connection.stream.RecordId
import org.springframework.data.redis.connection.stream.StreamRecords
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.data.redis.core.ReactiveStreamOperations
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.support.MessageBuilder
import reactor.core.publisher.Mono
import tools.jackson.databind.json.JsonMapper
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

private const val TRACE_ID = "0af7651916cd43dd8448eb211c80319c"
private const val SPAN_ID = "b7ad6b7169203331"
private const val OTHER_TRACE_ID = "0af7651916cd43dd8448eb211c80319d"
private const val OTHER_SPAN_ID = "b7ad6b7169203332"
private const val HEADER_VALUE = "00-$TRACE_ID-$SPAN_ID-01"

class RedisStreamTraceContextTest {
    private val tracer = SimpleTracer()
    private val traceParent = TraceParent(tracer, SimpleW3CPropagator(tracer))

    @AfterEach
    fun clearCurrentSpan() {
        tracer.withSpan(null as io.micrometer.tracing.Span?)
    }

    @Test
    fun `publisher stamps traceparent header when a span is active`() {
        val published = CopyOnWriteArrayList<Message<StringValue>>()
        val serializer = serializerCapturingPublishedMessages(published)

        runBlocking {
            tracer.withSpan(activeSpan()).use {
                newPublisher(serializer).publish(newMessage())
            }
        }

        assertThat(published).hasSize(1)
        assertThat(published[0].headers[TraceParent.HEADER]).isEqualTo(HEADER_VALUE)
    }

    @Test
    fun `publisher stamps the current span traceparent over a stale incoming header`() {
        val published = CopyOnWriteArrayList<Message<StringValue>>()
        val serializer = serializerCapturingPublishedMessages(published)
        val incomingHeader = "00-$OTHER_TRACE_ID-$OTHER_SPAN_ID-01"

        runBlocking {
            tracer.withSpan(activeSpan()).use {
                newPublisher(serializer).publish(newMessage(incomingHeader))
            }
        }

        assertThat(published[0].headers[TraceParent.HEADER]).isEqualTo(HEADER_VALUE)
    }

    @Test
    fun `publisher does not stamp traceparent when no span is active`() {
        val published = CopyOnWriteArrayList<Message<StringValue>>()
        val serializer = serializerCapturingPublishedMessages(published)

        runBlocking {
            publisherWithoutHeaderStamping(serializer).publish(newMessage())
        }

        assertThat(published[0].headers).doesNotContainKey(TraceParent.HEADER)
    }

    @Test
    fun `redis map record json serializer round trips the traceparent header`() {
        val serializer = jsonSerializer()

        val record = serializer.serialize(newMessage(HEADER_VALUE))
        val restored = serializer.deserialize(record, StringValue::class.java)

        assertThat(restored.headers[TraceParent.HEADER]).isEqualTo(HEADER_VALUE)
        assertThat(restored.payload).isEqualTo(StringValue.of("test"))
    }

    @Test
    fun `consumer restores the traceparent span context before sending to the channel`() {
        val observedTraceIds = CopyOnWriteArrayList<String?>()
        val observedSpans = CopyOnWriteArrayList<io.micrometer.tracing.test.simple.SimpleSpan>()
        val channelSent = CountDownLatch(1)

        val channel = mock<MessageChannel>()
        whenever(channel.send(any<Message<*>>(), any<Long>())).thenAnswer {
            observedTraceIds.add(tracer.currentSpan()?.context()?.traceId())
            observedSpans.add(tracer.currentSpan())
            channelSent.countDown()
            true
        }
        val consumer = newConsumer(recordWithHeaders(mapOf(TraceParent.HEADER to HEADER_VALUE)), observedTraceIds, channel)
        consumer.start()
        assertThat(channelSent.await(5, TimeUnit.SECONDS)).isTrue()
        consumer.stop()

        assertThat(observedTraceIds).containsExactly(TRACE_ID)
        assertThat(observedSpans.first().endTimestamp.toEpochMilli()).isGreaterThan(0)
    }

    @Test
    fun `consumer processes a message without traceparent header and starts a fresh trace`() {
        val observedTraceIds = CopyOnWriteArrayList<String?>()
        val channelSent = CountDownLatch(1)

        val channel = mock<MessageChannel>()
        whenever(channel.send(any<Message<*>>(), any<Long>())).thenAnswer {
            observedTraceIds.add(tracer.currentSpan()?.context()?.traceId())
            channelSent.countDown()
            true
        }

        val consumer = newConsumer(recordWithHeaders(emptyMap()), observedTraceIds, channel)
        consumer.start()
        assertThat(channelSent.await(5, TimeUnit.SECONDS)).isTrue()
        consumer.stop()

        assertThat(observedTraceIds).containsExactly(null as String?)
    }

    private fun newMessage(traceparent: String? = null): Message<StringValue> =
        MessageBuilder
            .withPayload(StringValue.of("test"))
            .let { builder ->
                if (traceparent != null) builder.setHeader(TraceParent.HEADER, traceparent) else builder
            }.build()

    private fun newPublisher(serializer: RedisMapRecordJsonSerializer): RedisStreamPublisher<StringValue> =
        RedisStreamPublisher(
            streamKey = "test-stream",
            reactiveRedisTemplate = reactiveRedisTemplate(),
            redisMapRecordJsonSerializer = serializer,
            lockRegistry = null,
            traceParent = traceParent,
        )

    private fun publisherWithoutHeaderStamping(serializer: RedisMapRecordJsonSerializer): RedisStreamPublisher<StringValue> =
        RedisStreamPublisher(
            streamKey = "test-stream",
            reactiveRedisTemplate = reactiveRedisTemplate(),
            redisMapRecordJsonSerializer = serializer,
            lockRegistry = null,
            traceParent = null,
        )

    private fun reactiveRedisTemplate(): ReactiveRedisTemplate<String, *> {
        val streamOperations: ReactiveStreamOperations<String, String, Any> = mock()
        whenever(streamOperations.add(any<MapRecord<String, Any, Any>>())).thenReturn(Mono.just(RecordId.of("1-1")))
        whenever(streamOperations.acknowledge(any<String>(), any<Record<String, Any>>())).thenReturn(Mono.just(1L))
        val template: ReactiveRedisTemplate<String, *> = mock()
        whenever(template.opsForStream<String, Any>()).thenReturn(streamOperations)
        return template
    }

    private fun jsonSerializer(): RedisMapRecordJsonSerializer =
        RedisMapRecordJsonSerializer(
            JsonMapper.builder().addModules(ProtobufModule()).build(),
        )

    private fun serializerCapturingPublishedMessages(published: CopyOnWriteArrayList<Message<StringValue>>): RedisMapRecordJsonSerializer {
        val serializer = mock<RedisMapRecordJsonSerializer>()
        whenever(serializer.serialize(any<Message<StringValue>>())).thenAnswer {
            published.add(it.getArgument(0))
            StreamRecords.string(mapOf("payload" to "{}", "headers" to "{}"))
        }
        return serializer
    }

    private fun activeSpan(): SimpleSpan =
        SimpleSpan().apply {
            context().setTraceId(TRACE_ID)
            context().setSpanId(SPAN_ID)
            context().setSampled(true)
            start()
        }

    private fun recordWithHeaders(headers: Map<String, String>): MapRecord<String, String, String> =
        MapRecord.create("test-stream", headers)

    private fun newConsumer(
        record: MapRecord<String, String, String>,
        observedTraceIds: CopyOnWriteArrayList<String?>,
        channel: MessageChannel,
    ): RedisStreamConsumer<com.google.protobuf.Any, Any> {
        val serializer = mock<RedisMapRecordJsonSerializer>()
        whenever(
            serializer.deserialize(any<MapRecord<String, String, String>>(), eq(com.google.protobuf.Any::class.java)),
        ).thenAnswer { invocation ->
            val deserializedRecord = invocation.getArgument<MapRecord<String, String, String>>(0)
            MessageBuilder
                .withPayload(
                    com.google.protobuf.Any
                        .getDefaultInstance(),
                ).let { builder ->
                    deserializedRecord.value[TraceParent.HEADER]
                        ?.let { builder.setHeader(TraceParent.HEADER, it) } ?: builder
                }.build()
        }
        return RedisStreamConsumer(
            channel = channel,
            serializer = serializer,
            type = com.google.protobuf.Any::class.java,
            recordStream = flowOf(record),
            errorChannel = null,
            mapper = null,
            reactiveRedisTemplate = reactiveRedisTemplate(),
            consumer = Consumer.from("test-group", "test-consumer"),
            lockProvider = null,
            traceParent = traceParent,
        )
    }
}
