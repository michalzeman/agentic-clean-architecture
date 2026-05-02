package mz.shared.connector.nats.jetstream.starter

import jakarta.validation.constraints.Positive
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty
import org.springframework.validation.annotation.Validated
import java.time.Duration

@Validated
@ConfigurationProperties(prefix = "nats.jetstream")
data class JetStreamProperties(
    val enabled: Boolean,
    @NestedConfigurationProperty
    val connection: ConnectionProperties,
    @NestedConfigurationProperty
    val streams: Map<String, StreamProperties>,
    @NestedConfigurationProperty
    val consumers: Map<String, ConsumerProperties>,
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

    data class StreamProperties(
        val enabled: Boolean,
        val subjects: List<String>,
        val storageType: String,
        val retentionPolicy: String,
        val discardPolicy: String,
        val maxMessages: Long,
        val maxBytes: Long,
        val maxAge: Duration?,
        val maxMessageSize: Int,
        val maxConsumers: Int,
        val duplicateWindow: Duration,
    )

    data class ConsumerProperties(
        val enabled: Boolean,
        val streamName: String,
        val filterSubject: String?,
        val deliverPolicy: String,
        val ackPolicy: String,
        @field:Positive
        val maxDeliver: Int,
        @field:Positive
        val maxAckPending: Int,
        @field:Positive
        val maxWaitingPulls: Int,
        @field:Positive
        val maxBatchSize: Int,
        val maxRequestExpires: Duration,
        val replayPolicy: String,
        val durable: Boolean,
    )
}
