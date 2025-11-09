package mz.integration.messaging.data.connector.redis

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import mz.data.platform.shared.domain.LockProvider
import org.springframework.integration.redis.util.RedisLockRegistry
import kotlin.concurrent.withLock

class RedisLockProvider(
    private val lockRegistry: RedisLockRegistry,
) : LockProvider {
    override suspend fun <T> withLock(
        keyLock: String,
        operation: suspend () -> T,
    ): T =
        lockRegistry.obtain(keyLock).withLock {
            runBlocking(Dispatchers.IO) {
                operation()
            }
        }
}
