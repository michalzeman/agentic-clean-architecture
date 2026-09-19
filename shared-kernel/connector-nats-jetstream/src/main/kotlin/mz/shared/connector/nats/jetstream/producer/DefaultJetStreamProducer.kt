package mz.shared.connector.nats.jetstream.producer

import io.nats.client.impl.Headers
import mz.shared.connector.nats.NatsConnectionFactory
import mz.shared.connector.nats.jetstream.message.JetStreamMessageConverter
import mz.shared.connector.observability.TraceParent
import org.slf4j.LoggerFactory
import java.time.Duration

private val logger = LoggerFactory.getLogger(DefaultJetStreamProducer::class.java)

/**
 * Default implementation of [JetStreamProducer] that publishes messages
 * to NATS JetStream using the provided connection factory and message converter.
 *
 * @param T The type of message payload
 * @param streamName The target stream name for publishing
 * @param connectionFactory Factory for obtaining NATS connections
 * @param converter Message converter for serialization
 * @param traceParent Stamps the current span as a W3C `traceparent` NATS header on outbound messages;
 * null means no stamping
 */
class DefaultJetStreamProducer<T>(
    override val streamName: String,
    private val connectionFactory: NatsConnectionFactory,
    private val converter: JetStreamMessageConverter<T>,
    private val traceParent: TraceParent? = null,
) : JetStreamProducer<T> {
    /**
     * Publishes a single message to JetStream.
     */
    override suspend fun publish(request: JetStreamPublishRequest<T>): PublishAck {
        val connection = connectionFactory.getConnection()
        val jetStream = connection.jetStream()
        val data = converter.serialize(request.payload)
        val headers = buildHeaders(request)

        val ack = jetStream.publish(request.subject, headers, data)
        connection.flush(Duration.ofSeconds(1))

        logger.debug(
            "Published message to JetStream: stream={}, subject={}, seq={}",
            streamName,
            request.subject,
            ack.seqno,
        )

        return PublishAck(
            streamName = ack.stream,
            sequenceNumber = ack.seqno,
            duplicate = ack.isDuplicate,
        )
    }

    /**
     * Publishes a batch of messages to JetStream.
     */
    override suspend fun publishBatch(messages: List<JetStreamPublishRequest<T>>): List<PublishAck> = messages.map { publish(it) }

    /**
     * Builds the outbound NATS headers: content type, deduplication message id, caller-supplied
     * headers, and the W3C `traceparent` of the current span when a [TraceParent] is wired in
     * (a caller-supplied `traceparent` is never overwritten).
     */
    private fun buildHeaders(request: JetStreamPublishRequest<T>): Headers {
        val headers = Headers()
        headers.add("Content-Type", converter.contentType)
        request.messageId?.let { headers.add("Nats-Msg-Id", it) }
        request.headers.forEach { (key, value) -> headers.add(key, value) }
        val traceparent = traceParent?.format()
        if (traceparent != null && request.headers[TraceParent.HEADER] == null) {
            headers.add(TraceParent.HEADER, traceparent)
        }
        return headers
    }
}
