package mz.shared.connector.nats.jetstream.consumer

import mz.shared.connector.nats.NatsConnectionFactory
import mz.shared.connector.nats.jetstream.message.JetStreamMessageConverter

/**
 * Builder for creating configured JetStream consumers.
 *
 * This is a convenience interface that combines configuration and creation
 * of consumers in a fluent API style.
 *
 * Example usage:
 * ```kotlin
 * val consumer = JetStreamConsumerBuilder(
 *     streamName = "ORDERS",
 *     subject = "orders.created",
 *     consumerName = "order-service-processor"
 * )
 *     .withConnectionFactory(connectionFactory)
 *     .withConverter(orderEventConverter)
 *     .withMessageHandler { message ->
 *         // Process the message
 *         println("Received: ${message.payload}")
 *     }
 *     .withBatchSize(50)
 *     .build<OrderEvent>()
 * ```
 */
class JetStreamConsumerBuilder<T>(
    val streamName: String,
    val subject: String,
    val consumerName: String,
) {
    init {
        require(streamName.isNotBlank()) { "Stream name must not be blank" }
        require(subject.isNotBlank()) { "Subject filter must not be blank" }
        require(consumerName.isNotBlank()) { "Consumer name must not be blank" }
    }

    private var batchSize: Int = 100

    private var converter: JetStreamMessageConverter<T>? = null

    private var connectionFactory: NatsConnectionFactory? = null

    private var durable: Boolean = true

    private var maxAckPending: Int = 1000

    private var ackWaitSeconds: Long = 30L

    private var maxDeliver: Int = 3

    private var autoAck: Boolean = false

    private var messageHandler: (suspend (JetStreamMessage<T>) -> Unit)? = null

    /**
     * Sets the batch size for message fetching.
     */
    fun withBatchSize(size: Int): JetStreamConsumerBuilder<T> {
        this.batchSize = size
        return this
    }

    /**
     * Sets the message converter for deserialization.
     */
    fun withConverter(converter: JetStreamMessageConverter<T>): JetStreamConsumerBuilder<T> {
        this.converter = converter
        return this
    }

    /**
     * Sets the NATS connection factory.
     */
    fun withConnectionFactory(connectionFactory: NatsConnectionFactory): JetStreamConsumerBuilder<T> {
        this.connectionFactory = connectionFactory
        return this
    }

    /**
     * Makes this a durable consumer (default: true).
     */
    fun durable(durable: Boolean): JetStreamConsumerBuilder<T> {
        this.durable = durable
        return this
    }

    /**
     * Sets the maximum number of pending acknowledgments.
     */
    fun withMaxAckPending(max: Int): JetStreamConsumerBuilder<T> {
        this.maxAckPending = max
        return this
    }

    /**
     * Sets how long the server waits for an ack before redelivering the message.
     */
    fun withAckWait(seconds: Long): JetStreamConsumerBuilder<T> {
        this.ackWaitSeconds = seconds
        return this
    }

    /**
     * Sets the maximum number of redelivery attempts before the message is considered failed.
     */
    fun withMaxDeliver(max: Int): JetStreamConsumerBuilder<T> {
        this.maxDeliver = max
        return this
    }

    /**
     * Sets the message handler callback that will be invoked for each consumed message.
     * Consumption starts immediately when the consumer is built.
     */
    fun withMessageHandler(handler: suspend (JetStreamMessage<T>) -> Unit): JetStreamConsumerBuilder<T> {
        this.messageHandler = handler
        return this
    }

    /**
     * Enables or disables automatic acknowledgement.
     *
     * When enabled, the consumer automatically acknowledges (ACK) messages
     * after the message handler completes successfully. If the handler throws
     * an exception, the message is negatively acknowledged (NAK) to trigger
     * redelivery.
     *
     * When disabled (default), the message handler is responsible for
     * calling [JetStreamConsumer.acknowledge] or [JetStreamConsumer.negativeAcknowledge] explicitly.
     *
     * @param enabled `true` to enable auto-acknowledge, `false` for manual (default)
     * @return this builder for fluent chaining
     */
    fun withAutoAck(enabled: Boolean): JetStreamConsumerBuilder<T> {
        this.autoAck = enabled
        return this
    }

    /**
     * Builds and returns the configured consumer.
     * Message consumption starts immediately upon building.
     */
    fun build(): JetStreamConsumer<T> {
        val factory =
            connectionFactory
                ?: throw IllegalStateException("Connection factory must be set before building consumer")
        val messageConverter =
            converter
                ?: throw IllegalStateException("Message converter must be set before building consumer")
        val handler =
            messageHandler
                ?: throw IllegalStateException("Message handler must be set before building consumer")

        // Create and return the consumer instance
        return DefaultJetStreamConsumer(
            consumerName = consumerName,
            streamName = streamName,
            subjectFilter = subject,
            connectionFactory = factory,
            converter = messageConverter,
            onMessage = handler,
            autoAck = autoAck,
            batchSize = batchSize,
            isDurable = durable,
            maxAckPending = maxAckPending,
            ackWaitSeconds = ackWaitSeconds,
            maxDeliver = maxDeliver,
        )
    }
}
