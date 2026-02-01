package mz.bank.system.tests

import kotlinx.coroutines.delay
import org.springframework.web.reactive.function.client.WebClientResponseException

/**
 * Retries a suspend function with exponential backoff.
 * Used to handle race conditions in distributed system tests.
 *
 * @param maxRetries Maximum number of retry attempts
 * @param initialDelayMillis Initial delay between retries in milliseconds (doubles after each attempt)
 * @param block The suspend function to execute with retry
 * @return The result of the successful block execution
 * @throws WebClientResponseException if all retries are exhausted
 * @throws IllegalStateException if retry logic fails unexpectedly
 */
suspend fun <T> retryWithBackoff(
    maxRetries: Int,
    initialDelayMillis: Long,
    block: suspend () -> T,
): T {
    var delayMillis = initialDelayMillis
    repeat(maxRetries) { attempt ->
        try {
            return block()
        } catch (e: WebClientResponseException) {
            if (attempt == maxRetries - 1) {
                throw e
            }
            delay(delayMillis)
            delayMillis *= 2
        }
    }
    throw IllegalStateException("Retry failed")
}
