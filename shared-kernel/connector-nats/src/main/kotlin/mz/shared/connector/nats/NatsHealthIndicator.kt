package mz.shared.connector.nats

import org.springframework.boot.health.contributor.AbstractHealthIndicator
import org.springframework.boot.health.contributor.Health

class NatsHealthIndicator(
    private val delegate: NatsConnectionHealthIndicator,
) : AbstractHealthIndicator() {
    override fun doHealthCheck(builder: Health.Builder) {
        val natsStatus = delegate.getStatus()
        if (natsStatus == io.nats.client.Connection.Status.CONNECTED) {
            builder.up().withDetail("natsStatus", natsStatus.name)
        } else {
            builder.down().withDetail("natsStatus", natsStatus.name)
        }
    }
}
