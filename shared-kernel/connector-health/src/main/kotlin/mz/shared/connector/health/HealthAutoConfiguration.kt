package mz.shared.connector.health

import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.health.autoconfigure.contributor.ConditionalOnEnabledHealthIndicator
import org.springframework.context.annotation.Bean
import org.springframework.integration.endpoint.AbstractEndpoint

@AutoConfiguration
@ConditionalOnClass(AbstractEndpoint::class)
class HealthAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnEnabledHealthIndicator("springIntegration")
    fun springIntegrationHealthIndicator(endpoints: ObjectProvider<AbstractEndpoint>): SpringIntegrationHealthIndicator =
        SpringIntegrationHealthIndicator(endpoints)
}
