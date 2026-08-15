package mz.shared.connector.observability

import io.micrometer.core.instrument.MeterRegistry
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass

/**
 * Anchor auto-configuration for the observability connector.
 *
 * Spring Boot auto-creates `PrometheusMeterRegistry` and exposes
 * `/actuator/prometheus` once `management.prometheus.metrics.export.enabled`
 * is true — set by `application-observability.yml` under the `observability` profile.
 *
 * Empty now. Phase 2 tracing beans (OTel exporter, ObservationRegistry customizers)
 * land here.
 */
@AutoConfiguration
@ConditionalOnClass(MeterRegistry::class)
class ObservabilityAutoConfiguration
