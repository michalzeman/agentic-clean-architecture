package mz.data.platform.shared.domain

interface LockProvider {
    suspend fun <T> withLock(
        keyLock: String,
        operation: suspend () -> T,
    ): T
}
