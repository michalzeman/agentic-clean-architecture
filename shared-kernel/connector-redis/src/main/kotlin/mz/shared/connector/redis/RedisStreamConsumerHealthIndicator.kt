package mz.shared.connector.redis

import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator

class RedisStreamConsumerHealthIndicator(
    private val consumers: ObjectProvider<RedisStreamConsumer<*, *>>,
) : HealthIndicator {
    override fun health(): Health {
        val all = consumers.toList()
        val stopped = all.filterNot { it.isRunning }
        return if (stopped.isEmpty()) {
            Health.up().withDetail("consumerCount", all.size).build()
        } else {
            Health
                .down()
                .withDetail("stoppedConsumers", stopped.map { it.consumer.name })
                .withDetail("consumerCount", all.size)
                .build()
        }
    }
}
