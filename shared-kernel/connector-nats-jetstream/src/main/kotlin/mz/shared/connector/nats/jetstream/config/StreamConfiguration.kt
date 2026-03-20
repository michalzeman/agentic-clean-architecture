package mz.shared.connector.nats.jetstream.config

import java.time.Duration

/**
 * Configuration for a JetStream stream.
 *
 * Streams store messages persistently and define retention policies.
 */
data class StreamConfiguration(
    val name: String,
    val subjects: List<String>,
    val storageType: StorageType = StorageType.FILE,
    val retentionPolicy: RetentionPolicy = RetentionPolicy.LIMITS,
    val discardPolicy: DiscardPolicy = DiscardPolicy.OLD,
    val maxMessages: Long = -1, // -1 = unlimited
    val maxBytes: Long = -1, // -1 = unlimited
    val maxAge: Duration? = null,
    val maxMessageSize: Int = -1,
    val maxConsumers: Int = -1,
    val replicationCount: Int = 1,
    val duplicateWindow: Duration = Duration.ofMinutes(2),
    val allowRollup: Boolean = false,
    val denyDelete: Boolean = false,
    val denyPurge: Boolean = false,
) {
    enum class StorageType {
        FILE,
        MEMORY,
    }

    enum class RetentionPolicy {
        LIMITS,
        INTEREST,
        WORK_QUEUE,
    }

    enum class DiscardPolicy {
        OLD,
        NEW,
    }
}

/**
 * Configuration for a JetStream consumer.
 *
 * Consumers track message delivery and maintain state.
 * Pull consumers are recommended for new implementations.
 */
data class ConsumerConfiguration(
    val name: String,
    val streamName: String,
    val filterSubject: String? = null,
    val deliverPolicy: DeliverPolicy = DeliverPolicy.ALL,
    val ackPolicy: AckPolicy = AckPolicy.EXPLICIT,
    val maxDeliver: Int = 3,
    val maxAckPending: Int = 1000,
    val maxWaitingPulls: Int = 512,
    val maxBatchSize: Int = 100,
    val maxRequestExpires: Duration = Duration.ofSeconds(30),
    val replayPolicy: ReplayPolicy = ReplayPolicy.INSTANT,
    val sampleFrequency: String? = null,
    val rateLimit: Long = 0, // Bytes per second, 0 = unlimited
    val heartbeatInterval: Duration? = null,
    val flowControl: Boolean = false,
    val headersOnly: Boolean = false,
    val backoffIntervals: List<Duration> = emptyList(),
) {
    enum class DeliverPolicy {
        ALL,
        LAST,
        NEW,
        BY_START_SEQUENCE,
        BY_START_TIME,
        LAST_PER_SUBJECT,
    }

    enum class AckPolicy {
        EXPLICIT,
        ALL,
        NONE,
    }

    enum class ReplayPolicy {
        INSTANT,
        ORIGINAL,
    }
}

/**
 * Factory for creating and managing streams and consumers.
 */
interface JetStreamConfigurationFactory {
    /**
     * Creates or updates a stream with the given configuration.
     */
    fun createOrUpdateStream(config: StreamConfiguration)

    /**
     * Creates or updates a consumer with the given configuration.
     */
    fun createOrUpdateConsumer(config: ConsumerConfiguration)

    /**
     * Deletes a stream and all its consumers.
     */
    fun deleteStream(streamName: String)

    /**
     * Deletes a specific consumer.
     */
    fun deleteConsumer(
        streamName: String,
        consumerName: String,
    )
}

/**
 * Exception thrown when stream/consumer configuration fails.
 */
class JetStreamConfigurationException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
