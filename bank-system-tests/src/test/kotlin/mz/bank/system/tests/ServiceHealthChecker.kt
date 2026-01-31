package mz.bank.system.tests

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import java.time.Duration

/**
 * Utility class to check if services are healthy and ready for testing.
 * Polls actuator health endpoints until services respond with UP status.
 */
class ServiceHealthChecker(
    private val webClient: WebClient,
    private val healthEndpoint: String,
    private val serviceName: String,
) {
    private val logger = LoggerFactory.getLogger(ServiceHealthChecker::class.java)

    /**
     * Waits for the service to be healthy by polling the actuator health endpoint.
     *
     * @param maxWaitDuration Maximum time to wait for the service to be healthy
     * @param pollInterval Interval between health checks
     * @throws IllegalStateException if the service does not become healthy within the max wait time
     */
    suspend fun waitForService(
        maxWaitDuration: Duration = Duration.ofSeconds(60),
        pollInterval: Duration = Duration.ofSeconds(2),
    ) {
        val startTime = System.currentTimeMillis()
        val maxWaitMillis = maxWaitDuration.toMillis()

        logger.info("Waiting for {} to be healthy at {}", serviceName, healthEndpoint)

        while (true) {
            try {
                val healthResponse = checkHealth()
                if (isHealthy(healthResponse)) {
                    logger.info("{} is healthy!", serviceName)
                    return
                }
                logger.debug("{} health status: {}", serviceName, healthResponse)
            } catch (e: Exception) {
                logger.debug("{} not ready yet: {}", serviceName, e.message)
            }

            val elapsedTime = System.currentTimeMillis() - startTime
            if (elapsedTime >= maxWaitMillis) {
                throw IllegalStateException(
                    "$serviceName did not become healthy within ${maxWaitDuration.seconds} seconds",
                )
            }

            delay(pollInterval.toMillis())
        }
    }

    /**
     * Checks the health endpoint and returns the response.
     */
    private suspend fun checkHealth(): Map<String, Any> =
        webClient
            .get()
            .uri(healthEndpoint)
            .retrieve()
            .awaitBody()

    /**
     * Determines if the health response indicates the service is UP.
     */
    private fun isHealthy(healthResponse: Map<String, Any>): Boolean {
        val status = healthResponse["status"] as? String
        return status == "UP"
    }
}

/**
 * Extension function to wait for multiple services to be healthy.
 */
suspend fun List<ServiceHealthChecker>.waitForAllServices(
    maxWaitDuration: Duration = Duration.ofSeconds(60),
    pollInterval: Duration = Duration.ofSeconds(2),
) = coroutineScope {
    this@waitForAllServices
        .map { checker ->
            async(Dispatchers.IO) {
                checker.waitForService(maxWaitDuration, pollInterval)
            }
        }.awaitAll()
}
