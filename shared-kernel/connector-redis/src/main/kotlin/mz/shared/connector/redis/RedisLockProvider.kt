package mz.shared.connector.redis

import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.await
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.mono
import kotlinx.coroutines.withContext
import mz.shared.domain.LockProvider
import org.apache.commons.logging.LogFactory
import org.redisson.api.RedissonClient
import org.redisson.api.RedissonReactiveClient
import reactor.core.publisher.Mono
import reactor.util.retry.Retry
import java.time.Duration
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit

private val logger = LogFactory.getLog(RedisLockProvider::class.java)

class RedisLockProvider(
    private val redissonClient: RedissonClient,
) : LockProvider {
    private val redissonReactiveClient: RedissonReactiveClient = redissonClient.reactive()

    override suspend fun <T> withLock(
        keyLock: String,
        operation: suspend () -> T,
    ): T = withAsyncLock(keyLock, operation)

    private suspend fun <T> withReactiveLock(
        keyLock: String,
        operation: suspend () -> T,
    ): T =
        withContext(IO) {
            val lock = redissonReactiveClient.getLock(keyLock)

            val threadId = Thread.currentThread().threadId()

            val monoOperation =
                mono {
                    operation()
                }

            lock
                .lock(10, TimeUnit.SECONDS)
                .retryWhen(
                    Retry
                        .backoff(3, Duration.ofMillis(100))
                        .maxBackoff(Duration.ofSeconds(1))
                        .doBeforeRetry { signal ->
                            logger.warn("Failed to acquire lock '$keyLock', attempt ${signal.totalRetries() + 1}/3", signal.failure())
                        },
                ).then(Mono.defer { monoOperation })
                .doFinally {
                    lock
                        .unlock(threadId)
                        .doOnError { error ->
                            logger.error("Failed to unlock '$keyLock'", error)
                        }.doOnSuccess {
                            logger.debug("Successfully unlocked '$keyLock'")
                        }.subscribe()
                }.awaitSingle()
        }

    /**
     * Async lock implementation using Redisson's CompletableFuture API.
     * Reserved for future use in scenarios requiring CompletableFuture compatibility.
     */
    private suspend fun <T> withAsyncLock(
        keyLock: String,
        operation: suspend () -> T,
    ): T {
        val lock = redissonClient.getLock(keyLock)

        // IMPORTANT: We use a random threadId instead of Thread.currentThread().threadId() because
        // in coroutines, the lock acquisition and unlock may happen on different threads due to
        // thread switching during suspension. Redisson's lock implementation requires the same
        // threadId for both lock and unlock operations. Using a consistent random ID ensures
        // unlock will succeed even if executed on a different thread than the one that acquired the lock.
        val threadId = ThreadLocalRandom.current().nextLong()

        var attempt = 0
        val maxAttempts = 3
        var lastError: Exception? = null

        while (true) {
            try {
                lock.lockAsync(10, TimeUnit.SECONDS, threadId).toCompletableFuture().await()
                break
            } catch (e: Exception) {
                lastError = e
                attempt++
                if (attempt < maxAttempts) {
                    val backoffMs = (100 * Math.pow(2.0, attempt.toDouble())).toLong().coerceAtMost(1000)
                    logger.warn("Failed to acquire lock '$keyLock', attempt $attempt/$maxAttempts", e)
                    delay(backoffMs)
                } else {
                    logger.error("Failed to acquire lock '$keyLock' after $maxAttempts attempts", e)
                    throw lastError
                }
            }
        }

        return try {
            operation()
        } finally {
            try {
                lock.unlockAsync(threadId).toCompletableFuture().await()
                logger.debug("Successfully unlocked '$keyLock'")
            } catch (e: Exception) {
                logger.error("Failed to unlock '$keyLock'", e)
            }
        }
    }
}
