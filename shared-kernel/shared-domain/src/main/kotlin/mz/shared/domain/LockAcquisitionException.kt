package mz.shared.domain

/**
 * Exception thrown when a distributed lock cannot be acquired.
 * This is a domain-level exception that abstracts over infrastructure-specific
 * lock implementations (e.g., Redisson, etc.).
 */
class LockAcquisitionException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
