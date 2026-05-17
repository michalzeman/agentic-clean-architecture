package mz.shared.connector.nats.jetstream.starter

import jakarta.validation.constraints.Positive
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty
import org.springframework.validation.annotation.Validated
import java.time.Duration

/**
 * Configuration properties for NATS JetStream integration.
 *
 * These properties are loaded from the "nats.jetstream" prefix in the application configuration.
 * Use this to configure the NATS connection, JetStream streams, and consumer settings.
 *
 * Example configuration:
 * <pre>
 * nats:
 *   jetstream:
 *     enabled: true
 *     connection:
 *       server-url: nats://localhost:4222
 *       max-reconnects: 10
 *     streams:
 *       - name: events
 *         subjects:
 *           - my.subject
 * </pre>
 *
 * @property enabled Whether JetStream is enabled
 * @property connection Connection configuration for NATS
 * @property streams List of stream configurations to create
 */
@Validated
@ConfigurationProperties(prefix = "nats.jetstream")
data class JetStreamProperties(
    val enabled: Boolean,
    @field:NestedConfigurationProperty
    val connection: ConnectionProperties,
    val streams: List<StreamConfiguration>,
) {
    data class ConnectionProperties(
        val serverUrl: String,
        val connectionName: String?,
        @field:Positive
        val maxReconnects: Int,
        @field:Positive
        val reconnectWaitMillis: Long,
        @field:Positive
        val connectionTimeoutMillis: Long,
        @field:Positive
        val pingIntervalMillis: Long,
    )

    data class StreamConfiguration(
        val name: String,
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
}
