package mz.shared.connector.observability

import io.micrometer.tracing.Span
import io.micrometer.tracing.TraceContext
import io.micrometer.tracing.propagation.Propagator
import io.micrometer.tracing.test.simple.SimpleSpan
import io.micrometer.tracing.test.simple.SimpleTracer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

private const val TRACE_ID = "0af7651916cd43dd8448eb211c80319c"
private const val SPAN_ID = "b7ad6b7169203331"

class TraceParentTest {
    private val tracer = SimpleTracer()
    private val traceParent = TraceParent(tracer, SimpleW3CPropagator(tracer))

    @AfterEach
    fun clearCurrentSpan() {
        tracer.withSpan(null as Span?)
    }

    @Test
    fun `formats traceparent from current span`() {
        val span =
            SimpleSpan().apply {
                context().setTraceId(TRACE_ID)
                context().setSpanId(SPAN_ID)
                context().setSampled(true)
                start()
            }

        tracer.withSpan(span).use {
            assertThat(traceParent.format()).isEqualTo("00-$TRACE_ID-$SPAN_ID-01")
        }
        assertThat(traceParent.format()).isNull()
    }

    @Test
    fun `format derives the flags field from the sampling decision`() {
        val unsampled =
            SimpleSpan().apply {
                context().setTraceId(TRACE_ID)
                context().setSpanId(SPAN_ID)
                context().setSampled(false)
                start()
            }

        tracer.withSpan(unsampled).use {
            assertThat(traceParent.format()).isEqualTo("00-$TRACE_ID-$SPAN_ID-00")
        }
    }

    @Test
    fun `missing or malformed traceparent is tolerated`() {
        assertThat(TraceParent.tryParse(null)).isNull()
        assertThat(TraceParent.tryParse("garbage")).isNull()
        assertThat(TraceParent.tryParse("00-only-three-parts")).isNull()
    }

    @Test
    fun `all-zero trace id or span id is rejected`() {
        assertThat(TraceParent.tryParse("00-00000000000000000000000000000000-$SPAN_ID-01")).isNull()
        assertThat(TraceParent.tryParse("00-$TRACE_ID-0000000000000000-01")).isNull()
    }

    @Test
    fun `restore puts extracted span in scope with the header trace id`() {
        val header = "00-$TRACE_ID-$SPAN_ID-01"

        traceParent.restore(header)

        assertThat(tracer.currentSpan()?.context()?.traceId()).isEqualTo(TRACE_ID)
        assertThat(tracer.currentSpan()?.context()?.parentId()).isEqualTo(SPAN_ID)
    }

    @Test
    fun `restore without a header or with garbage does not touch the current span`() {
        assertThat(tracer.currentSpan()).isNull()

        traceParent.restore(null)
        traceParent.restore("garbage")

        assertThat(tracer.currentSpan()).isNull()
    }

    @Test
    fun `restore without a header or with garbage closes the previous scope`() {
        traceParent.restore("00-$TRACE_ID-$SPAN_ID-01")
        assertThat(tracer.currentSpan()?.context()?.traceId()).isEqualTo(TRACE_ID)

        traceParent.restore(null)
        assertThat(tracer.currentSpan()).isNull()

        traceParent.restore("00-$TRACE_ID-$SPAN_ID-01")
        assertThat(tracer.currentSpan()?.context()?.traceId()).isEqualTo(TRACE_ID)

        traceParent.restore("garbage")
        assertThat(tracer.currentSpan()).isNull()
    }

    @Test
    fun `restore replaces the previous scope on the same thread`() {
        traceParent.restore("00-$TRACE_ID-$SPAN_ID-01")
        val firstSpan = tracer.currentSpan()

        traceParent.restore("00-$TRACE_ID-aaaaaaaaaaaaaaaa-01")

        assertThat(tracer.currentSpan()?.context()?.parentId()).isEqualTo("aaaaaaaaaaaaaaaa")
        assertThat(firstSpan).isNotSameAs(tracer.currentSpan())
    }

    @Test
    fun `closing the restored handle clears the scope and ends the span`() {
        val handle = traceParent.restore("00-$TRACE_ID-$SPAN_ID-01")
        val span = tracer.currentSpan()

        handle?.close()

        assertThat(tracer.currentSpan()).isNull()
        assertThat(span?.endTimestamp?.toEpochMilli()).isGreaterThan(0L)
    }

    @Test
    fun `closing the restored handle twice does not throw or end the scope beyond once`() {
        val handle = traceParent.restore("00-$TRACE_ID-$SPAN_ID-01")

        handle?.close()
        handle?.close()

        assertThat(tracer.currentSpan()).isNull()
    }

    @Test
    fun `restore never throws even when the propagator fails`() {
        val failing = TraceParent(tracer, ThrowingPropagator())

        assertThatCode { failing.restore("00-$TRACE_ID-$SPAN_ID-01") }.doesNotThrowAnyException()

        assertThat(tracer.currentSpan()).isNull()
    }

    @Test
    fun `withRestoredSpan runs the block with the restored span active then ends scope and span`() {
        runBlocking {
            var observedInside: SimpleSpan? = null

            traceParent.withRestoredSpan("00-$TRACE_ID-$SPAN_ID-01") {
                observedInside = tracer.currentSpan()
            }

            assertThat(observedInside?.context()?.traceId()).isEqualTo(TRACE_ID)
            assertThat(observedInside?.endTimestamp?.toEpochMilli()).isGreaterThan(0L)
            assertThat(tracer.currentSpan()).isNull()
        }
    }

    @Test
    fun `withRestoredSpan without a header runs the block with no span active and no throw`() {
        runBlocking {
            val observed = traceParent.withRestoredSpan(null as String?) { tracer.currentSpan()?.context()?.traceId() }

            assertThat(observed).isNull()
            assertThat(tracer.currentSpan()).isNull()
        }
    }

    @Test
    fun `withRestoredSpan keeps the restored span active across a coroutine dispatch`() {
        runBlocking {
            var observedOnIo: String? = null

            traceParent.withRestoredSpan("00-$TRACE_ID-$SPAN_ID-01") {
                withContext(Dispatchers.IO) {
                    observedOnIo = tracer.currentSpan()?.context()?.traceId()
                }
            }

            assertThat(observedOnIo).isEqualTo(TRACE_ID)
            assertThat(tracer.currentSpan()).isNull()
        }
    }
}

private class ThrowingPropagator : Propagator {
    override fun fields(): List<String> = listOf(TraceParent.HEADER)

    override fun <C : Any> inject(
        context: TraceContext,
        carrier: C?,
        setter: Propagator.Setter<C>,
    ): Unit = throw IllegalStateException("propagator must never be used to inject in this test")

    override fun <C : Any> extract(
        carrier: C?,
        getter: Propagator.Getter<C>,
    ): Span.Builder = throw IllegalStateException("propagator exploded")
}
