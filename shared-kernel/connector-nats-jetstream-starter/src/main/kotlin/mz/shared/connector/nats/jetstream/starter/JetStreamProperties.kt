package mz.shared.connector.nats.jetstream.starter

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty
import java.time.Duration

/**
 * Spring Boot configuration properties for NATS JetStream.
 */
@ConfigurationProperties(prefix = "nats.jetstream")
data class JetStreamProperties(
    val enabled: Boolean = true,
    @NestedConfigurationProperty
    val connection: ConnectionProperties = ConnectionProperties(),
    @NestedConfigurationProperty
    val streams: Map<String, StreamProperties> = emptyMap(),
    @NestedConfigurationProperty
    val consumers: Map<String, ConsumerProperties> = emptyMap(),
) {
    data class ConnectionProperties(
        val serverUrl: String = "nats://localhost:4222",
        val connectionName: String? = null,
        val maxReconnects: Int = 10,
        val reconnectWaitMillis: Long = 2000,
        val connectionTimeoutMillis: Long = 5000,
        val pingIntervalMillis: Long = 10000,
    )

    data class StreamProperties(
        val enabled: Boolean = true,
        val subjects: List<String> = emptyList(),
        val storageType: String = "FILE",
        val retentionPolicy: String = "LIMITS",
        val discardPolicy: String = "OLD",
        val maxMessages: Long = -1,
        val maxBytes: Long = -1,
        val maxAge: Duration? = null,
        val maxMessageSize: Int = -1,
        val maxConsumers: Int = -1,
        val duplicateWindow: Duration = Duration.ofMinutes(2),
    )

    data class ConsumerProperties(
        val enabled: Boolean = true,
        val streamName: String,
        val filterSubject: String? = null,
        val deliverPolicy: String = "ALL",
        val ackPolicy: String = "EXPLICIT",
        val maxDeliver: Int = 3,
        val maxAckPending: Int = 1000,
        val maxWaitingPulls: Int = 512,
        val maxBatchSize: Int = 100,
        val maxRequestExpires: Duration = Duration.ofSeconds(30),
        val replayPolicy: String = "INSTANT",
        val durable: Boolean = true,
    )
}
