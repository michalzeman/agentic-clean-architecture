package mz.shared.connector.nats.jetstream.consumer

import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator

class JetStreamConsumerHealthIndicator(
    private val consumers: ObjectProvider<JetStreamConsumer<*>>,
) : HealthIndicator {
    override fun health(): Health {
        val all = consumers.toList()
        val stopped = all.filterNot { it.isRunning }
        return if (stopped.isEmpty()) {
            Health.up().withDetail("consumers", all.map { it.consumerName }).build()
        } else {
            Health
                .down()
                .withDetail("stoppedConsumers", stopped.map { it.consumerName })
                .withDetail("runningConsumers", all.filter { it.isRunning }.map { it.consumerName })
                .build()
        }
    }
}
