package mz.bank.transaction.adapter.nats.jetstream.config

import mz.bank.account.contract.proto.BankAccountEvent
import mz.bank.transaction.adapter.nats.jetstream.BankTransactionJetStreamProperties
import mz.bank.transaction.adapter.nats.jetstream.inbound.toInboundEvent
import mz.bank.transaction.adapter.nats.jetstream.mapper.BankAccountEventJetStreamMessageConverter
import mz.shared.connector.nats.NatsConnectionFactory
import mz.shared.connector.nats.jetstream.consumer.JetStreamConsumer
import mz.shared.connector.nats.jetstream.consumer.JetStreamConsumerBuilder
import mz.shared.connector.nats.jetstream.consumer.JetStreamMessage
import mz.shared.connector.nats.jetstream.message.JetStreamMessageConverter
import mz.shared.connector.observability.TraceParent
import org.slf4j.LoggerFactory
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
 * Sets up the consumer and message converter for handling bank account events.
 */
@Configuration
@Profile("nats-jetstream")
@EnableConfigurationProperties(BankTransactionJetStreamProperties::class)
class InboundJetStreamConfiguration(
    private val properties: BankTransactionJetStreamProperties,
) {
    private val logger = LoggerFactory.getLogger(InboundJetStreamConfiguration::class.java)

    @Bean
    fun bankAccountEventJetStreamConverter(
        @Qualifier("protobufObjectMapper") objectMapper: ObjectMapper,
    ): JetStreamMessageConverter<BankAccountEvent> = BankAccountEventJetStreamMessageConverter(objectMapper)

    /**
     * Sends the converted JetStream message to the inbound channel, carrying the
     * W3C `traceparent` header along: the channel never moves ThreadLocals, so the
     * header is the propagation contract across the JDBC-backed queue poller hops
     * (the span restore already happened in the consumer before this handler ran).
     */
    internal fun sendToInboundChannel(
        message: JetStreamMessage<BankAccountEvent>,
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
        bankAccountEventJetStreamConverter: JetStreamMessageConverter<BankAccountEvent>,
        inboundBankAccountEventsChannel: MessageChannel,
        traceParent: TraceParent?,
    ): JetStreamConsumer<BankAccountEvent> =
        JetStreamConsumerBuilder<BankAccountEvent>(
            streamName = properties.bankAccountEventsStream,
            subject = properties.bankAccountEventsSubject,
            consumerName = properties.consumerName,
        ).withConnectionFactory(connectionFactory)
            .withConverter(bankAccountEventJetStreamConverter)
            .withAutoAck(true)
            .withTraceParent(traceParent)
            .withMessageHandler { message ->
                val protoEvent = message.payload
                logger.info("[Consumer:Boundary] - eventCase={}", protoEvent.eventCase)
                sendToInboundChannel(message, inboundBankAccountEventsChannel)
            }.build()
}
