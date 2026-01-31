package mz.bank.system.tests

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

/**
 * Configuration properties for bank system tests.
 */
@ConfigurationProperties(prefix = "bank-system-tests")
data class TestProperties(
    val accountServiceUrl: String = "http://localhost:8080",
    val transactionServiceUrl: String = "http://localhost:8081",
    val healthEndpoint: String = "/actuator/health",
)

/**
 * Spring configuration for bank system tests.
 * Provides WebClient beans for both services and health checkers.
 */
@Configuration
@EnableConfigurationProperties(TestProperties::class)
class BankSystemTestConfiguration(
    private val testProperties: TestProperties,
) {
    /**
     * WebClient for bank-account service.
     */
    @Bean
    fun accountServiceWebClient(): WebClient =
        WebClient
            .builder()
            .baseUrl(testProperties.accountServiceUrl)
            .build()

    /**
     * WebClient for bank-transaction service.
     */
    @Bean
    fun transactionServiceWebClient(): WebClient =
        WebClient
            .builder()
            .baseUrl(testProperties.transactionServiceUrl)
            .build()

    /**
     * Health checker for bank-account service.
     */
    @Bean
    fun accountServiceHealthChecker(): ServiceHealthChecker =
        ServiceHealthChecker(
            webClient = accountServiceWebClient(),
            healthEndpoint = testProperties.healthEndpoint,
            serviceName = "bank-account-service",
        )

    /**
     * Health checker for bank-transaction service.
     */
    @Bean
    fun transactionServiceHealthChecker(): ServiceHealthChecker =
        ServiceHealthChecker(
            webClient = transactionServiceWebClient(),
            healthEndpoint = testProperties.healthEndpoint,
            serviceName = "bank-transaction-service",
        )
}
