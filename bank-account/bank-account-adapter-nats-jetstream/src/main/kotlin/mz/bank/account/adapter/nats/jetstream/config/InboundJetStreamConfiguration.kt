package mz.bank.account.adapter.nats.jetstream.config

import mz.bank.account.adapter.nats.jetstream.BankAccountJetStreamProperties
import mz.bank.account.adapter.nats.jetstream.inbound.toInboundEvent
import mz.bank.account.adapter.nats.jetstream.mapper.BankTransactionEventJetStreamMessageConverter
import mz.bank.transaction.contract.proto.BankTransactionEvent
import mz.shared.connector.nats.NatsConnectionFactory
import mz.shared.connector.nats.jetstream.consumer.JetStreamConsumer
import mz.shared.connector.nats.jetstream.consumer.JetStreamConsumerBuilder
import mz.shared.connector.nats.jetstream.consumer.JetStreamMessage
import mz.shared.connector.nats.jetstream.message.JetStreamMessageConverter
import mz.shared.connector.observability.TraceParent
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.support.MessageBuilder
import tools.jackson.databind.ObjectMapper

/**
 * Configuration for inbound JetStream consumption.
 * Sets up the consumer and message converter for handling bank transaction events.
 */
@Configuration
@Profile("nats-jetstream")
@EnableConfigurationProperties(BankAccountJetStreamProperties::class)
class InboundJetStreamConfiguration(
    private val properties: BankAccountJetStreamProperties,
) {
    @Bean
    fun bankTransactionEventJetStreamConverter(
        @Qualifier("protobufObjectMapper") objectMapper: ObjectMapper,
    ): JetStreamMessageConverter<BankTransactionEvent> = BankTransactionEventJetStreamMessageConverter(objectMapper)

    /**
     * Sends the converted JetStream message to the inbound channel, carrying the
     * W3C `traceparent` header along: the channel never moves ThreadLocals, so the
     * header is the propagation contract across the JDBC-backed queue poller hops
     * (the span restore already happened in the consumer before this handler ran).
     */
    internal fun sendToInboundChannel(
        message: JetStreamMessage<BankTransactionEvent>,
        channel: MessageChannel,
    ) {
        val inboundEvent = message.payload.toInboundEvent()
        val builder = MessageBuilder.withPayload(inboundEvent)
        message.headers[TraceParent.HEADER]?.firstOrNull()?.let { builder.setHeader(TraceParent.HEADER, it) }
        val sent = channel.send(builder.build())
        if (!sent) throw IllegalStateException("Channel rejected message: consumer=${properties.consumerName}")
    }

    @Bean
    fun jetStreamConsumer(
        connectionFactory: NatsConnectionFactory,
        bankTransactionEventJetStreamConverter: JetStreamMessageConverter<BankTransactionEvent>,
        inboundBankTransactionEventsChannel: MessageChannel,
        traceParent: TraceParent?,
    ): JetStreamConsumer<BankTransactionEvent> =
        JetStreamConsumerBuilder<BankTransactionEvent>(
            streamName = properties.bankTransactionEventsStream,
            subject = properties.bankTransactionEventsSubject,
            consumerName = properties.consumerName,
        ).withConnectionFactory(connectionFactory)
            .withConverter(bankTransactionEventJetStreamConverter)
            .withAutoAck(true)
            .withTraceParent(traceParent)
            .withMessageHandler { message ->
                sendToInboundChannel(message, inboundBankTransactionEventsChannel)
            }.build()
}
