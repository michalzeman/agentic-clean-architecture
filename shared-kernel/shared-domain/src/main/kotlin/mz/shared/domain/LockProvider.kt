package mz.shared.domain

interface LockProvider {
    suspend fun <T> withLock(
        keyLock: String,
        operation: suspend () -> T,
    ): T
}
