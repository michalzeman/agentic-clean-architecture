package mz.shared.connector.observability

import io.micrometer.tracing.Span
import io.micrometer.tracing.TraceContext
import io.micrometer.tracing.Tracer
import io.micrometer.tracing.propagation.Propagator
import kotlinx.coroutines.ThreadContextElement
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.CoroutineContext

private val logger = LoggerFactory.getLogger(TraceParent::class.java)

/**
 * Formats and restores the W3C `traceparent` header at message-transport boundaries.
 *
 * The pair (tracer, propagator) must come from the same tracer stack — in production the
 * OpenTelemetry beans auto-configured by Boot, in tests the micrometer-tracing-test Simple kit.
 * A missing or malformed header never fails processing: it degrades to a fresh trace.
 */
class TraceParent(
    private val tracer: Tracer,
    private val propagator: Propagator,
) {
    // At most one open restored scope per thread, scoped to this instance (not global).
    private val openScope = ThreadLocal.withInitial { AtomicReference<RestoredSpan?>(null) }

    fun format(): String? {
        val context: TraceContext = tracer.currentSpan()?.context() ?: return null
        // Explicitly unsampled writes 00; null or true writes 01 (the always-on sampling policy).
        val flags = if (context.sampled() == false) "00" else "01"
        return "00-${context.traceId()}-${context.spanId()}-$flags"
    }

    /**
     * Puts the span extracted from the header into scope on the current thread and returns
     * its handle; closing the handle both removes the scope and ends the span. A missing
     * or malformed header closes any previous scope and clears the current span instead: a
     * headerless message must never inherit the prior trace on a pooled consumer thread.
     * Never throws at the caller — a failing extraction also closes any previous scope and
     * degrades to a fresh trace.
     *
     * Internal/test-visible lifecycle hook. Production consumers use [withRestoredSpan],
     * which ties the close to the same call and cannot leak the handle across threads.
     */
    internal fun restore(traceparent: String?): AutoCloseable? =
        try {
            val span = extractSpan(traceparent)
            closeOpenScope()
            if (span == null) {
                null
            } else {
                val scope = tracer.withSpan(span)
                RestoredSpan(span, scope, openScope.get()).also { openScope.get().set(it) }
            }
        } catch (e: Exception) {
            closeOpenScope()
            logger.debug("Failed to restore trace context from 'traceparent'; keeping a fresh trace", e)
            null
        }

    /**
     * Extracts and starts the span carried by the header, or null when the header is missing
     * or malformed. Never throws; a failing extraction degrades to null.
     */
    private fun extractSpan(traceparent: String?): Span? =
        try {
            val header = tryParse(traceparent) ?: return null
            propagator.extract(carrierOf(header), GETTER).start()
        } catch (e: Exception) {
            logger.debug("Failed to restore trace context from 'traceparent'; keeping a fresh trace", e)
            null
        }

    /** Closes any scope a previous [restore] left open on this thread. */
    private fun closeOpenScope() {
        openScope.get()?.getAndSet(null)?.close()
        openScope.remove()
    }

    /**
     * Runs [block] with the span restored from the header in scope, or with a fresh trace
     * (and any previous scope closed) when the header is missing or malformed. The restored
     * span follows [block] across coroutine dispatches: it is re-scoped on every thread the
     * block resumes on and the scope is released on the same thread it was opened on. The
     * span is ended exactly once, after the block completes; the restore part never throws.
     *
     * Scopes opened here are tracked by the [RestoredSpanContext] coroutine context element,
     * not by the per-thread holder that [restore] uses; the holder only tracks scopes opened
     * by [restore] itself.
     */
    suspend fun <T> withRestoredSpan(
        header: String?,
        block: suspend () -> T,
    ): T {
        closeOpenScope()
        val span = extractSpan(header) ?: return block()
        return withContext(RestoredSpanContext(tracer, span)) {
            try {
                block()
            } finally {
                span.end()
            }
        }
    }

    private fun carrierOf(header: String): Map<String, String> = mapOf(HEADER to header)

    /**
     * Re-opens the restored span on every thread the wrapped coroutine resumes on and closes
     * it again when the coroutine leaves that thread, so the restored span follows execution
     * across `withContext`/dispatcher hops instead of pinning to the caller thread.
     */
    private class RestoredSpanContext(
        private val tracer: Tracer,
        private val span: Span,
    ) : ThreadContextElement<Tracer.SpanInScope?> {
        companion object Key : CoroutineContext.Key<RestoredSpanContext>

        override val key: CoroutineContext.Key<*> get() = Key

        override fun updateThreadContext(context: CoroutineContext): Tracer.SpanInScope? = tracer.withSpan(span)

        override fun restoreThreadContext(
            context: CoroutineContext,
            oldState: Tracer.SpanInScope?,
        ) {
            oldState?.close()
        }
    }

    private class RestoredSpan(
        private val span: Span,
        private val scope: Tracer.SpanInScope,
        private val holder: AtomicReference<RestoredSpan?>,
    ) : AutoCloseable {
        private val openedOn = Thread.currentThread()
        private val closed = AtomicBoolean(false)

        override fun close() {
            if (closed.compareAndSet(false, true)) {
                if (Thread.currentThread() !== openedOn) {
                    logger.debug(
                        "Restored trace span closed on a different thread than restored on; the surrounding execution dispatched mid-scope",
                    )
                }
                holder.compareAndSet(this, null)
                scope.close()
                span.end()
            }
        }
    }

    companion object {
        const val HEADER = "traceparent"

        private const val VERSION = 0
        private const val TRACE_ID = 1
        private const val SPAN_ID = 2
        private const val FLAGS = 3

        /** Version `ff` is never valid; longer trace id parts are observed in the wild. */
        private val VERSION_HEX = Regex("^[0-9a-f]{2}$")
        private val TRACE_ID_HEX = Regex("^[0-9a-f]{32}$")
        private val SPAN_ID_HEX = Regex("^[0-9a-f]{16}$")
        private const val ALL_ZERO_TRACE_ID = "00000000000000000000000000000000"
        private const val ALL_ZERO_SPAN_ID = "0000000000000000"

        private val GETTER = Propagator.Getter<Map<String, String>> { carrier, key -> carrier[key] }

        /**
         * Returns the normalized lowercase header or null when it is missing or malformed.
         * Validates the W3C shape: four parts, two-hex version and flags, 32-hex trace id,
         * 16-hex span id, not all-zero trace or span id. Any two-hex version except `ff`
         * parses — newer minor versions must stay forward-compatible.
         */
        fun tryParse(traceparent: String?): String? {
            val header = traceparent?.trim()?.lowercase() ?: return null
            if (header.isBlank()) return null
            val parts = header.split("-")
            if (parts.size != 4) return null
            if (!parts[VERSION].matches(VERSION_HEX) || parts[VERSION] == "ff") return null
            if (!parts[TRACE_ID].matches(TRACE_ID_HEX) || parts[TRACE_ID] == ALL_ZERO_TRACE_ID) return null
            if (!parts[SPAN_ID].matches(SPAN_ID_HEX) || parts[SPAN_ID] == ALL_ZERO_SPAN_ID) return null
            if (!parts[FLAGS].matches(VERSION_HEX)) return null
            return header
        }
    }
}
