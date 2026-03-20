package mz.shared.connector.nats

import io.nats.client.Connection

/**
 * Factory for creating and managing NATS connections.
 * Implementations should handle connection lifecycle and health.
 */
interface NatsConnectionFactory {
    /**
     * Gets or creates a connection to the NATS server.
     * Connection should be cached and reused.
     */
    fun getConnection(): Connection

    /**
     * Returns the cached connection without creating a new one.
     * Returns null if no connection has been established yet.
     * Use this for read-only observation (e.g. health checks) to avoid side effects.
     */
    fun peekConnection(): Connection?

    /**
     * Closes the connection and releases resources.
     * This should be called on application shutdown.
     */
    fun close()
}

/**
 * Exception thrown when NATS connection operations fail.
 */
class NatsConnectionException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
