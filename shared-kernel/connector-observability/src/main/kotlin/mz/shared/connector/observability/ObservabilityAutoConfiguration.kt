package mz.shared.connector.observability

import io.micrometer.context.ContextRegistry
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.tracing.Tracer
import io.micrometer.tracing.contextpropagation.ObservationAwareSpanThreadLocalAccessor
import io.micrometer.tracing.propagation.Propagator
import mz.shared.domain.ObservabilityMeters
import org.springframework.beans.factory.DisposableBean
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import reactor.core.publisher.Hooks
import java.util.concurrent.atomic.AtomicBoolean

@AutoConfiguration
@ConditionalOnClass(MeterRegistry::class)
class ObservabilityAutoConfiguration {
    @Bean
    fun observabilityMeters(meterRegistry: MeterRegistry): ObservabilityMeters = ObservabilityMetersImpl(meterRegistry)

    /**
     * Registers the W3C trace-context helpers against the tracer beans auto-configured by
     * Boot's tracing auto-configuration. Class-guarded so contexts without the tracing
     * bridge still load the metrics-only part of this auto-configuration, and additionally
     * property-guarded so contexts with `management.tracing.enabled=false` also load the
     * metrics-only part.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(Tracer::class)
    @ConditionalOnProperty(name = ["management.tracing.enabled"], havingValue = "true", matchIfMissing = true)
    class ObservabilityTracingConfiguration {
        @Bean
        @ConditionalOnMissingBean
        fun traceParent(
            tracer: Tracer,
            propagator: Propagator,
        ): TraceParent = TraceParent(tracer, propagator)

        /**
         * Makes the tracer's current span visible to ContextSnapshot capture so traces can
         * follow execution onto poller/consumer-container threads. Boot 4's tracing
         * auto-configuration registers handlers for ObservationRegistry, not this
         * ThreadLocal accessor — nothing else registers it, so it is registered here from
         * context startup (the registry is a global singleton).
         */
        @Bean
        @ConditionalOnMissingBean
        fun spanThreadLocalAccessor(tracer: Tracer): ObservationAwareSpanThreadLocalAccessor {
            val accessor = ObservationAwareSpanThreadLocalAccessor(tracer)
            ContextRegistry.getInstance().registerThreadLocalAccessor(accessor)
            return accessor
        }

        /** Removes the accessor from the global registry when the context shuts down. */
        @Bean
        fun spanThreadLocalAccessorCleanup(accessor: ObservationAwareSpanThreadLocalAccessor): DisposableBean =
            DisposableBean {
                ContextRegistry.getInstance().removeThreadLocalAccessor(ObservationAwareSpanThreadLocalAccessor.KEY)
            }
    }

    /**
     * Propagates ThreadLocal values (span, MDC) across Reactor boundaries. Enabled once per
     * JVM; the hook is a global Reactor scheduler hook.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = ["reactor.core.publisher.Hooks"])
    class ObservabilityReactorTracingConfiguration {
        init {
            if (CONTEXT_PROPAGATION_HOOK_ENABLED.compareAndSet(false, true)) {
                Hooks.enableAutomaticContextPropagation()
            }
        }

        private companion object {
            private val CONTEXT_PROPAGATION_HOOK_ENABLED = AtomicBoolean(false)
        }
    }
}
