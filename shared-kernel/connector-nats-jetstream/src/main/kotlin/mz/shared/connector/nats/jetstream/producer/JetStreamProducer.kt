package mz.shared.connector.nats.jetstream.producer

/**
 * Contract for JetStream message producers.
 *
 * Producers publish messages to JetStream streams.
 * Supports optional deduplication via message ID.
 */
interface JetStreamProducer<T> {
    /**
     * The target stream name for publishing.
     */
    val streamName: String

    /**
     * Publishes a message to JetStream.
     *
     * @param request The publish request, including subject, payload, and optional message ID for deduplication
     * @return Publish acknowledgment with sequence number
     */
    suspend fun publish(request: JetStreamPublishRequest<T>): PublishAck

    /**
     * Publishes multiple messages in a batch for efficiency.
     *
     * @param messages List of messages to publish
     * @return List of publish acknowledgments
     */
    suspend fun publishBatch(messages: List<JetStreamPublishRequest<T>>): List<PublishAck>
}

/**
 * Request data for publishing a single message.
 */
data class JetStreamPublishRequest<T>(
    val subject: String,
    val payload: T,
    val messageId: String? = null,
    val headers: Map<String, String> = emptyMap(),
)

/**
 * Acknowledgment from JetStream after successful publish.
 */
data class PublishAck(
    val streamName: String,
    val sequenceNumber: Long,
    val duplicate: Boolean = false,
)

/**
 * Exception thrown when publishing fails.
 */
class JetStreamPublishException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
