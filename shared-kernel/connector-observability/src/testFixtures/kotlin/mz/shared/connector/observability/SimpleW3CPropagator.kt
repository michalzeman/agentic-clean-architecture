package mz.shared.connector.observability

import io.micrometer.tracing.Span
import io.micrometer.tracing.TraceContext
import io.micrometer.tracing.propagation.Propagator
import io.micrometer.tracing.test.simple.SimpleTracer

/**
 * Test fixture shared by the trace-propagation tests: the Simple counterpart of the
 * production OtelPropagator — the pair (SimpleTracer, propagator) must come from the same
 * tracer stack. Extract builds a child span from the traceparent header; inject stamps it.
 */
class SimpleW3CPropagator(
    private val tracer: SimpleTracer,
) : Propagator {
    override fun fields(): List<String> = listOf(TraceParent.HEADER)

    override fun <C : Any> inject(
        context: TraceContext,
        carrier: C?,
        setter: Propagator.Setter<C>,
    ) {
        setter.set(carrier!!, TraceParent.HEADER, "00-${context.traceId()}-${context.spanId()}-01")
    }

    override fun <C : Any> extract(
        carrier: C?,
        getter: Propagator.Getter<C>,
    ): Span.Builder {
        val header = getter.get(carrier!!, TraceParent.HEADER) ?: return tracer.spanBuilder()
        val parts = header.split("-")
        val parent =
            io.micrometer.tracing.test.simple.SimpleTraceContext().apply {
                setTraceId(parts[1])
                setSpanId(parts[2])
            }
        return tracer.spanBuilder().setParent(parent)
    }
}
