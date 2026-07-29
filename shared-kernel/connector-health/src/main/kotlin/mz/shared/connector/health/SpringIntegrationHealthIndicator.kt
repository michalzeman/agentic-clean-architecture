package mz.shared.connector.health

import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator
import org.springframework.integration.endpoint.AbstractEndpoint

class SpringIntegrationHealthIndicator(
    private val endpoints: ObjectProvider<AbstractEndpoint>,
) : HealthIndicator {
    override fun health(): Health {
        val all = endpoints.toList()
        val stopped = all.filterNot { it.isRunning }
        return if (stopped.isEmpty()) {
            Health.up().withDetail("endpointCount", all.size).build()
        } else {
            Health
                .down()
                .withDetail("stoppedEndpoints", stopped.map { it.beanName ?: it.javaClass.simpleName })
                .withDetail("endpointCount", all.size)
                .build()
        }
    }
}
