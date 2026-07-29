package mz.shared.connector.nats

import io.nats.client.Connection
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.health.contributor.Status

class NatsHealthIndicatorTest {
    private fun delegate(
        healthy: Boolean,
        status: Connection.Status,
    ) = object : NatsConnectionHealthIndicator {
        override fun isHealthy(): Boolean = healthy

        override fun getStatus(): Connection.Status = status
    }

    @Test
    fun `should report UP when connection healthy`() {
        val health = NatsHealthIndicator(delegate(true, Connection.Status.CONNECTED)).health()
        assertThat(health.status).isEqualTo(Status.UP)
        assertThat(health.details["natsStatus"]).isEqualTo("CONNECTED")
    }

    @Test
    fun `should report DOWN when connection missing`() {
        val health = NatsHealthIndicator(delegate(false, Connection.Status.DISCONNECTED)).health()
        assertThat(health.status).isEqualTo(Status.DOWN)
        assertThat(health.details["natsStatus"]).isEqualTo("DISCONNECTED")
    }
}
