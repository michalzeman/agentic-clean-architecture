package mz.shared.connector.observability

import io.micrometer.tracing.test.simple.SimpleTracer
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.integration.channel.ExecutorChannel
import org.springframework.integration.endpoint.EventDrivenConsumer
import org.springframework.integration.handler.MessageProcessor
import org.springframework.integration.handler.ServiceActivatingHandler
import org.springframework.messaging.Message
import org.springframework.messaging.support.MessageBuilder
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private const val TRACE_ID = "0af7651916cd43dd8448eb211c80319c"
private const val SPAN_ID = "b7ad6b7169203331"
private const val HEADER_VALUE = "00-$TRACE_ID-$SPAN_ID-01"

/**
 * The Spring Integration hop behind the transport mapping: a message sent to a real
 * [ExecutorChannel] carries the traceparent header onto the handler thread, where the
 * [ServiceActivatingHandler] (the machinery behind `@ServiceActivator`) restores the
 * span from it. The channel never moves ThreadLocals; the header is the contract.
 */
class ChannelHopTraceContextTest {
    private val tracer = SimpleTracer()
    private val traceParent = TraceParent(tracer, SimpleW3CPropagator(tracer))
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    @AfterEach
    fun tearDown() {
        tracer.withSpan(null as io.micrometer.tracing.Span?)
        executor.shutdownNow()
    }

    @Test
    fun `traceparent crosses a message channel hop and restores the span on the handler thread`() {
        val observedTraceIds = CopyOnWriteArrayList<String?>()
        val observedHeaders = CopyOnWriteArrayList<String?>()
        val handlerThreads = CopyOnWriteArrayList<String>()
        val handled = CountDownLatch(1)
        val senderThread = Thread.currentThread().name

        val channel = ExecutorChannel(executor)
        val handler =
            ServiceActivatingHandler(
                MessageProcessor<Any> { message: Message<*> ->
                    observedHeaders.add(message.headers[TraceParent.HEADER] as? String)
                    handlerThreads.add(Thread.currentThread().name)
                    runBlocking {
                        traceParent.withRestoredSpan(message.headers[TraceParent.HEADER] as? String) {
                            observedTraceIds.add(tracer.currentSpan()?.context()?.traceId())
                        }
                    }
                    handled.countDown()
                    null
                },
            )
        val endpoint = EventDrivenConsumer(channel, handler)
        endpoint.start()
        try {
            channel.send(
                MessageBuilder
                    .withPayload("event")
                    .setHeader(TraceParent.HEADER, HEADER_VALUE)
                    .build(),
            )
            assertThat(handled.await(5, TimeUnit.SECONDS)).isTrue()
        } finally {
            endpoint.stop()
        }

        assertThat(observedHeaders).containsExactly(HEADER_VALUE)
        assertThat(handlerThreads[0]).isNotEqualTo(senderThread)
        assertThat(observedTraceIds).containsExactly(TRACE_ID)
    }
}
