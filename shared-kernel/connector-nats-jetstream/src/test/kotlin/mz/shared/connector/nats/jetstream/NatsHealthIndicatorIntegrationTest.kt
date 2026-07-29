package mz.shared.connector.nats.jetstream

import mz.shared.connector.nats.DefaultNatsConnectionFactory
import mz.shared.connector.nats.DefaultNatsConnectionHealthIndicator
import mz.shared.connector.nats.NatsConnectionProperties
import mz.shared.connector.nats.NatsHealthIndicator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.health.contributor.Status
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName

@Testcontainers
class NatsHealthIndicatorIntegrationTest {
    companion object {
        @Container
        @JvmStatic
        val nats: GenericContainer<*> =
            GenericContainer(DockerImageName.parse("nats:2.10.7-alpine"))
                .withExposedPorts(4222)
    }

    @Test
    fun `should report UP against real NATS server`() {
        val factory =
            DefaultNatsConnectionFactory(
                NatsConnectionProperties(serverUrl = "nats://${nats.host}:${nats.getMappedPort(4222)}"),
            )
        try {
            factory.getConnection()
            val health = NatsHealthIndicator(DefaultNatsConnectionHealthIndicator(factory)).health()
            assertThat(health.status).isEqualTo(Status.UP)
        } finally {
            factory.close()
        }
    }
}
