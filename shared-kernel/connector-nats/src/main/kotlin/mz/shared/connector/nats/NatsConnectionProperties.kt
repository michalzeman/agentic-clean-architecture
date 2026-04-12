package mz.shared.connector.nats

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuration properties for NATS server connection.
 */
@ConfigurationProperties(prefix = "nats.connection")
data class NatsConnectionProperties(
    val serverUrl: String = "nats://localhost:4222",
    val connectionName: String? = null,
    val maxReconnects: Int = 10,
    val reconnectWaitMillis: Long = 2000,
    val connectionTimeoutMillis: Long = 5000,
    val pingIntervalMillis: Long = 10000,
)
