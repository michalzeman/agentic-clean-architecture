package mz.bank.system.tests

import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.runBlocking
import mz.bank.system.tests.wiring.BankSystemTestConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.web.reactive.function.client.WebClient

/**
 * System integration tests for bank services.
 * Verifies both services are healthy and accessible.
 */
@Tag("systemChecks")
@SpringBootTest(
    classes = [BankSystemTestConfiguration::class],
)
@TestInstance(Lifecycle.PER_CLASS)
class BankSystemIntegrationTest {
    @Autowired
    private lateinit var accountServiceWebClient: WebClient

    @Autowired
    private lateinit var transactionServiceWebClient: WebClient

    @Autowired
    private lateinit var accountServiceHealthChecker: ServiceHealthChecker

    @Autowired
    private lateinit var transactionServiceHealthChecker: ServiceHealthChecker

    @BeforeAll
    fun setUp() {
        runBlocking {
            // Wait for both services to be healthy before running tests
            listOf(
                accountServiceHealthChecker,
                transactionServiceHealthChecker,
            ).waitForAllServices()
        }
    }

    @Test
    fun `bank-account-service should be healthy`() {
        runBlocking {
            val health =
                accountServiceWebClient
                    .get()
                    .uri("/actuator/health")
                    .retrieve()
                    .bodyToMono(Map::class.java)
                    .awaitSingle()

            assertThat(health).isNotNull
            assertThat(health?.get("status")).isEqualTo("UP")
        }
    }

    @Test
    fun `bank-transaction-service should be healthy`() {
        runBlocking {
            val health =
                transactionServiceWebClient
                    .get()
                    .uri("/actuator/health")
                    .retrieve()
                    .bodyToMono(Map::class.java)
                    .awaitSingle()

            assertThat(health).isNotNull
            assertThat(health?.get("status")).isEqualTo("UP")
        }
    }
}
