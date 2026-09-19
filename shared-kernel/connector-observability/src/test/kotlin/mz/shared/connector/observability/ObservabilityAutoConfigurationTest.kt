package mz.shared.connector.observability

import io.micrometer.context.ContextRegistry
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import io.micrometer.tracing.contextpropagation.ObservationAwareSpanThreadLocalAccessor
import mz.shared.domain.ObservabilityMeters
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.micrometer.metrics.autoconfigure.CompositeMeterRegistryAutoConfiguration
import org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration
import org.springframework.boot.micrometer.metrics.autoconfigure.export.prometheus.PrometheusMetricsExportAutoConfiguration
import org.springframework.boot.micrometer.tracing.autoconfigure.MicrometerTracingAutoConfiguration
import org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.OpenTelemetryTracingAutoConfiguration
import org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.otlp.OtlpTracingAutoConfiguration
import org.springframework.boot.opentelemetry.autoconfigure.OpenTelemetrySdkAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import reactor.core.publisher.Flux
import reactor.core.scheduler.Schedulers

class ObservabilityAutoConfigurationTest {
    @AfterEach
    fun removeGlobalSpanAccessor() {
        ContextRegistry.getInstance().removeThreadLocalAccessor(ObservationAwareSpanThreadLocalAccessor.KEY)
    }

    private val contextRunner: ApplicationContextRunner =
        ApplicationContextRunner()
            .withConfiguration(
                AutoConfigurations.of(
                    MetricsAutoConfiguration::class.java,
                    CompositeMeterRegistryAutoConfiguration::class.java,
                    PrometheusMetricsExportAutoConfiguration::class.java,
                    ObservabilityAutoConfiguration::class.java,
                    // From spring-boot-micrometer-tracing-opentelemetry
                    OpenTelemetryTracingAutoConfiguration::class.java,
                    OtlpTracingAutoConfiguration::class.java,
                    MicrometerTracingAutoConfiguration::class.java,
                    // From spring-boot-opentelemetry: provides Resource + OpenTelemetrySdk beans
                    OpenTelemetrySdkAutoConfiguration::class.java,
                ),
            )

    @Test
    fun `should register PrometheusMeterRegistry bean when observability profile active`() {
        contextRunner
            .withPropertyValues("management.prometheus.metrics.export.enabled=true")
            .run { ctx ->
                assertThat(ctx).hasSingleBean(PrometheusMeterRegistry::class.java)
            }
    }

    @Test
    fun `should not register PrometheusMeterRegistry when observability export disabled`() {
        contextRunner
            .withPropertyValues("management.prometheus.metrics.export.enabled=false")
            .run { ctx ->
                assertThat(ctx).doesNotHaveBean(PrometheusMeterRegistry::class.java)
            }
    }

    @Test
    fun `should register ObservabilityMeters bean when meter registry present`() {
        contextRunner
            .withPropertyValues("management.prometheus.metrics.export.enabled=true")
            .run { ctx ->
                assertThat(ctx).hasSingleBean(ObservabilityMeters::class.java)
            }
    }

    @Test
    fun `should register Tracer bean from classpath auto-configuration`() {
        contextRunner
            .withPropertyValues("management.prometheus.metrics.export.enabled=true")
            .run { ctx ->
                assertThat(ctx).hasSingleBean(io.micrometer.tracing.Tracer::class.java)
            }
    }

    @Test
    fun `should register Propagator bean from classpath auto-configuration`() {
        contextRunner
            .withPropertyValues("management.prometheus.metrics.export.enabled=true")
            .run { ctx ->
                assertThat(ctx).hasSingleBean(io.micrometer.tracing.propagation.Propagator::class.java)
            }
    }

    @Test
    fun `should register TraceParent bean from classpath auto-configuration`() {
        contextRunner
            .withPropertyValues("management.prometheus.metrics.export.enabled=true")
            .run { ctx ->
                assertThat(ctx).hasSingleBean(TraceParent::class.java)
            }
    }

    @Test
    fun `should register the span thread-local accessor that Boot tracing does not`() {
        contextRunner
            .withPropertyValues("management.prometheus.metrics.export.enabled=true")
            .run { ctx ->
                assertThat(ctx).hasSingleBean(ObservationAwareSpanThreadLocalAccessor::class.java)
                val accessor: io.micrometer.context.ThreadLocalAccessor<*>? =
                    ContextRegistry
                        .getInstance()
                        .getThreadLocalAccessors()
                        .firstOrNull { it.key() == ObservationAwareSpanThreadLocalAccessor.KEY }
                assertThat(accessor).isNotNull()
            }
    }

    @Test
    fun `should not register TraceParent and should still start when tracing is disabled`() {
        contextRunner
            .withPropertyValues(
                "management.prometheus.metrics.export.enabled=true",
                "management.tracing.enabled=false",
            ).run { ctx ->
                assertThat(ctx).hasNotFailed()
                assertThat(ctx).doesNotHaveBean(TraceParent::class.java)
            }
    }

    @Test
    fun `auto-configuration enables reactor automatic context propagation`() {
        contextRunner
            .withPropertyValues("management.prometheus.metrics.export.enabled=true")
            .run { ctx ->
                val tracer = ctx.getBean(io.micrometer.tracing.Tracer::class.java)
                val span = tracer.nextSpan().start()
                val observedTraceId: String? =
                    tracer.withSpan(span).use {
                        Flux
                            .just("event")
                            .subscribeOn(Schedulers.boundedElastic())
                            .map { tracer.currentSpan()?.context()?.traceId() ?: NO_PROPAGATED_TRACE }
                            .blockLast()
                    }
                assertThat(observedTraceId).isEqualTo(span.context().traceId())
            }
    }

    private companion object {
        private const val NO_PROPAGATED_TRACE = "no-propagated-trace"
    }

    @Test
    fun `closing the context removes the span thread-local accessor from the registry`() {
        contextRunner
            .withPropertyValues("management.prometheus.metrics.export.enabled=true")
            .run { ctx ->
                assertThat(
                    ContextRegistry
                        .getInstance()
                        .getThreadLocalAccessors()
                        .firstOrNull { it.key() == ObservationAwareSpanThreadLocalAccessor.KEY },
                ).isNotNull()
            }
        assertThat(
            ContextRegistry
                .getInstance()
                .getThreadLocalAccessors()
                .firstOrNull { it.key() == ObservationAwareSpanThreadLocalAccessor.KEY },
        ).isNull()
    }
}
