package mz.bank.account.observability

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.env.Environment
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["spring.profiles.active=postgres-nats,observability"],
)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BankAccountObservabilityIntegrationTest {
    @Autowired
    private lateinit var environment: Environment

    private lateinit var webTestClient: WebTestClient

    @BeforeAll
    fun setUp() {
        val port =
            environment.getProperty("local.server.port", Int::class.java)
                ?: error("local.server.port not set — is webEnvironment = RANDOM_PORT?")
        webTestClient = WebTestClient.bindToServer().baseUrl("http://localhost:$port").build()
    }

    companion object {
        @JvmStatic
        val redis: GenericContainer<*> =
            GenericContainer("redis:7-alpine")
                .withExposedPorts(6379)

        @JvmStatic
        val postgres: PostgreSQLContainer<*> =
            PostgreSQLContainer("postgres:16-alpine")
                .withDatabaseName("bank_db")
                .withUsername("bank_user")
                .withPassword("bank_pass")

        @JvmStatic
        val nats: GenericContainer<*> =
            GenericContainer("nats:2.10.7-alpine")
                .withExposedPorts(4222)
                .withCommand("--jetstream")

        @DynamicPropertySource
        @JvmStatic
        fun registerContainerProperties(registry: DynamicPropertyRegistry) {
            redis.start()
            postgres.start()
            nats.start()

            registry.add("spring.data.redis.host") { redis.host }
            registry.add("spring.data.redis.port") { redis.getMappedPort(6379) }
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
            registry.add("nats.connection.server-url") {
                "nats://${nats.host}:${nats.getMappedPort(4222)}"
            }
            registry.add("nats.jetstream.connection.server-url") {
                "nats://${nats.host}:${nats.getMappedPort(4222)}"
            }
        }
    }

    @AfterAll
    fun tearDown() {
        redis.stop()
        postgres.stop()
        nats.stop()
    }

    @Test
    fun `should activate the observability profile`() {
        assertThat(environment.activeProfiles).contains("observability")
    }

    @Test
    fun `should expose prometheus endpoint with JVM and HTTP metrics`() {
        // Generate HTTP traffic so http_server_requests_seconds records.
        webTestClient
            .get()
            .uri("/actuator/health")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .returnResult()

        val prometheusBody =
            webTestClient
                .get()
                .uri("/actuator/prometheus")
                .exchange()
                .expectStatus()
                .isOk
                .expectBody(String::class.java)
                .returnResult()
                .responseBody!!

        assertThat(prometheusBody).contains("jvm_memory_used_bytes")
        assertThat(prometheusBody).contains("http_server_requests_seconds")
    }
}
