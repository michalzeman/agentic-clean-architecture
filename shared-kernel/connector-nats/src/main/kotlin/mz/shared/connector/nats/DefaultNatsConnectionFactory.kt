package mz.shared.connector.nats

import io.nats.client.Connection
import io.nats.client.ConnectionListener
import io.nats.client.Nats
import io.nats.client.Options
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.DisposableBean
import java.util.concurrent.atomic.AtomicReference

/**
 * Default implementation of NatsConnectionFactory.
 *
 * This implementation lazily creates a single connection that is cached and reused.
 * The connection is automatically reconnected by the NATS client based on the
 * configured reconnect settings.
 *
 * Thread-safe for concurrent access.
 *
 * @param properties Configuration for the NATS connection
 */
class DefaultNatsConnectionFactory(
    private val properties: NatsConnectionProperties,
) : NatsConnectionFactory,
    DisposableBean {
    private val logger = LoggerFactory.getLogger(DefaultNatsConnectionFactory::class.java)
    private val connectionRef = AtomicReference<Connection?>(null)

    override fun getConnection(): Connection {
        connectionRef.get()?.let { existingConnection ->
            if (existingConnection.status == Connection.Status.CONNECTED ||
                existingConnection.status == Connection.Status.RECONNECTING
            ) {
                return existingConnection
            }
        }

        synchronized(this) {
            connectionRef.get()?.let { existingConnection ->
                if (existingConnection.status == Connection.Status.CONNECTED ||
                    existingConnection.status == Connection.Status.RECONNECTING
                ) {
                    return existingConnection
                }
            }

            val newConnection = createConnection()
            connectionRef.set(newConnection)
            return newConnection
        }
    }

    override fun peekConnection(): Connection? = connectionRef.get()

    override fun close() {
        synchronized(this) {
            val connection: Connection? = connectionRef.getAndSet(null)
            if (connection != null) {
                try {
                    connection.close()
                    logger.info("NATS connection closed successfully")
                } catch (e: Exception) {
                    logger.error("Error closing NATS connection", e)
                }
            }
        }
    }

    override fun destroy() {
        close()
    }

    private fun createConnection(): Connection {
        try {
            val builder =
                Options
                    .Builder()
                    .server(properties.serverUrl)
                    .maxReconnects(properties.maxReconnects)
                    .reconnectWait(java.time.Duration.ofMillis(properties.reconnectWaitMillis))
                    .connectionTimeout(java.time.Duration.ofMillis(properties.connectionTimeoutMillis))
                    .pingInterval(java.time.Duration.ofMillis(properties.pingIntervalMillis))
                    .connectionListener(
                        ConnectionListener { _, type ->
                            when (type) {
                                ConnectionListener.Events.CONNECTED ->
                                    logger.info("NATS connection established to ${properties.serverUrl}")

                                ConnectionListener.Events.DISCONNECTED ->
                                    logger.warn("NATS connection disconnected from ${properties.serverUrl}")

                                ConnectionListener.Events.RECONNECTED ->
                                    logger.info("NATS connection reconnected to ${properties.serverUrl}")

                                ConnectionListener.Events.RESUBSCRIBED ->
                                    logger.debug("NATS subscriptions resubscribed")

                                ConnectionListener.Events.CLOSED ->
                                    logger.info("NATS connection closed")

                                else -> logger.debug("NATS connection event: $type")
                            }
                        },
                    )

            properties.connectionName?.let { name ->
                builder.connectionName(name)
            }

            return Nats.connect(builder.build())
        } catch (e: Exception) {
            throw NatsConnectionException(
                "Failed to connect to NATS server at ${properties.serverUrl}",
                e,
            )
        }
    }
}

/**
 * Health indicator for NATS connection.
 */
interface NatsConnectionHealthIndicator {
    /**
     * Checks if the NATS connection is healthy.
     */
    fun isHealthy(): Boolean

    /**
     * Gets the current connection status.
     */
    fun getStatus(): Connection.Status
}

/**
 * Default implementation of NatsConnectionHealthIndicator.
 */
class DefaultNatsConnectionHealthIndicator(
    private val connectionFactory: NatsConnectionFactory,
) : NatsConnectionHealthIndicator {
    override fun isHealthy(): Boolean {
        val connection = connectionFactory.peekConnection() ?: return false
        return connection.status == Connection.Status.CONNECTED
    }

    override fun getStatus(): Connection.Status {
        val connection = connectionFactory.peekConnection() ?: return Connection.Status.DISCONNECTED
        return connection.status
    }
}
