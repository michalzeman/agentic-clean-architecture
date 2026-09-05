package mz.shared.connector.observability

import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import mz.shared.domain.ObservabilityMeters
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.micrometer.metrics.autoconfigure.CompositeMeterRegistryAutoConfiguration
import org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration
import org.springframework.boot.micrometer.metrics.autoconfigure.export.prometheus.PrometheusMetricsExportAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner

class ObservabilityAutoConfigurationTest {
    private val contextRunner: ApplicationContextRunner =
        ApplicationContextRunner()
            .withConfiguration(
                AutoConfigurations.of(
                    MetricsAutoConfiguration::class.java,
                    CompositeMeterRegistryAutoConfiguration::class.java,
                    PrometheusMetricsExportAutoConfiguration::class.java,
                    ObservabilityAutoConfiguration::class.java,
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
}
