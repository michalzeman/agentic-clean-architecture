package mz.shared.connector.observability

import io.micrometer.core.instrument.MeterRegistry
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.context.annotation.Bean

@AutoConfiguration
@ConditionalOnClass(MeterRegistry::class)
class ObservabilityAutoConfiguration {
    @Bean
    fun observabilityMeters(meterRegistry: MeterRegistry): ObservabilityMeters = ObservabilityMeters(meterRegistry)
}
