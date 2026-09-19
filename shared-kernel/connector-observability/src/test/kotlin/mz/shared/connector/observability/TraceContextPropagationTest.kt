package mz.shared.connector.observability

import io.micrometer.context.ContextRegistry
import io.micrometer.tracing.Span
import io.micrometer.tracing.contextpropagation.ObservationAwareSpanThreadLocalAccessor
import io.micrometer.tracing.test.simple.SimpleSpan
import io.micrometer.tracing.test.simple.SimpleTracer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import reactor.core.publisher.Flux
import reactor.core.publisher.Hooks
import reactor.core.scheduler.Schedulers
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Context-propagation hops behind the transport mapping: a span restored from the
 * traceparent header must follow execution onto the thread that runs the handler —
 * via the registered [ObservationAwareSpanThreadLocalAccessor] plus a
 * [io.micrometer.context.ContextSnapshot] (poller/consumer-container pattern), across a
 * coroutine dispatch, and through Reactor pipelines when the Reactor hook is enabled.
 * A missing or garbage traceparent never fails processing: it propagates a fresh trace.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TraceContextPropagationTest {
    private val tracer = SimpleTracer()
    private val observedTraceIds = CopyOnWriteArrayList<String?>()

    /**
     * The span accessor is registered in production by this module's
     * [ObservabilityAutoConfiguration] (`spanThreadLocalAccessor` bean, nested inside
     * `ObservabilityTracingConfiguration`) — Boot 4's tracing auto-configuration does not
     * register it. Without it, ContextSnapshot capture finds no trace context and the span
     * stays on its originating thread (pinned by the RED run of this test class).
     */
    @BeforeAll
    fun registerSpanAccessor() {
        ContextRegistry.getInstance().registerThreadLocalAccessor(ObservationAwareSpanThreadLocalAccessor(tracer))
    }

    @AfterAll
    fun unregisterSpanAccessor() {
        ContextRegistry.getInstance().removeThreadLocalAccessor(ObservationAwareSpanThreadLocalAccessor.KEY)
    }

    @AfterEach
    fun clearTestState() {
        observedTraceIds.clear()
        tracer.withSpan(null as Span?)
    }

    @Test
    fun `span active on one thread continues on the handler thread`() {
        val span = activeSpan()

        val snapshot =
            tracer.withSpan(span).use {
                io.micrometer.context.ContextSnapshotFactory
                    .builder()
                    .build()
                    .captureAll()
            }

        runOnSeparateThread {
            snapshot.setThreadLocals().use {
                observedTraceIds.add(tracer.currentSpan()?.context()?.traceId())
            }
        }

        assertThat(observedTraceIds).containsExactly(TRACE_ID)
    }

    @Test
    fun `span continues across a coroutine dispatch to the io dispatcher`() {
        runBlocking {
            val span = activeSpan()

            tracer.withSpan(span).use {
                val snapshot =
                    io.micrometer.context.ContextSnapshotFactory
                        .builder()
                        .build()
                        .captureAll()
                withContext(Dispatchers.IO) {
                    snapshot.setThreadLocals().use {
                        observedTraceIds.add(tracer.currentSpan()?.context()?.traceId())
                    }
                }
            }

            assertThat(observedTraceIds).containsExactly(TRACE_ID)
        }
    }

    @Test
    fun `span propagates through a reactor pipeline on another scheduler`() {
        val span = activeSpan()

        Hooks.enableAutomaticContextPropagation()
        try {
            val observedTraceId: String? =
                tracer.withSpan(span).use {
                    Flux
                        .just("event")
                        .subscribeOn(Schedulers.boundedElastic())
                        .map { tracer.currentSpan()?.context()?.traceId() ?: MISSING_TRACE }
                        .blockLast()
                }

            assertThat(observedTraceId).isEqualTo(TRACE_ID)
        } finally {
            // The auto-configuration enables this hook once per JVM and never re-enables it;
            // disabling it here would strip propagation from every later context in this JVM.
            Hooks.enableAutomaticContextPropagation()
        }
    }

    @Test
    fun `missing or garbage traceparent propagates a fresh trace without throwing`() {
        val traceParent = TraceParent(tracer, SimpleW3CPropagator(tracer))

        traceParent.restore(null)
        traceParent.restore("garbage")
        traceParent.restore("00-$OTHER_TRACE_ID-$OTHER_SPAN_ID-01-extra-part")

        val snapshot =
            io.micrometer.context.ContextSnapshotFactory
                .builder()
                .build()
                .captureAll()
        runOnSeparateThread {
            snapshot.setThreadLocals().use {
                observedTraceIds.add(tracer.currentSpan()?.context()?.traceId())
            }
        }

        assertThat(observedTraceIds).containsExactly(null as String?)
    }

    private fun activeSpan(): SimpleSpan =
        SimpleSpan().apply {
            context().setTraceId(TRACE_ID)
            context().setSpanId(SPAN_ID)
            start()
        }

    private fun runOnSeparateThread(block: () -> Unit) {
        val done = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()
        Thread {
            try {
                block()
            } catch (t: Throwable) {
                failure.set(t)
            } finally {
                done.countDown()
            }
        }.apply { start() }

        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue()
        failure.get()?.let { throw it }
    }

    private companion object {
        private const val TRACE_ID = "0af7651916cd43dd8448eb211c80319c"
        private const val SPAN_ID = "b7ad6b7169203331"
        private const val OTHER_TRACE_ID = "0af7651916cd43dd8448eb211c80319d"
        private const val OTHER_SPAN_ID = "b7ad6b7169203332"
        private const val MISSING_TRACE = "no-trace"
    }
}
