package mz.bank.account.adapter.nats.jetstream.config

import com.fasterxml.jackson.databind.ObjectMapper
import mz.bank.account.adapter.nats.jetstream.BankAccountJetStreamProperties
import mz.bank.account.adapter.nats.jetstream.inbound.toInboundEvent
import mz.bank.account.adapter.nats.jetstream.mapper.BankTransactionEventJetStreamMessageConverter
import mz.bank.transaction.contract.proto.BankTransactionEvent
import mz.shared.connector.nats.NatsConnectionFactory
import mz.shared.connector.nats.jetstream.consumer.JetStreamConsumer
import mz.shared.connector.nats.jetstream.consumer.JetStreamConsumerBuilder
import mz.shared.connector.nats.jetstream.message.JetStreamMessageConverter
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.support.MessageBuilder

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

    @Bean
    fun jetStreamConsumer(
        connectionFactory: NatsConnectionFactory,
        bankTransactionEventJetStreamConverter: JetStreamMessageConverter<BankTransactionEvent>,
        inboundBankTransactionEventsChannel: MessageChannel,
    ): JetStreamConsumer<BankTransactionEvent> {
        val consumer =
            JetStreamConsumerBuilder<BankTransactionEvent>(
                streamName = properties.bankTransactionEventsStream,
                subject = properties.bankTransactionEventsSubject,
                consumerName = properties.consumerName,
            ).withConnectionFactory(connectionFactory)
                .withConverter(bankTransactionEventJetStreamConverter)
                .withMessageHandler { message ->
                    val inboundEvent = message.payload.toInboundEvent()
                    inboundBankTransactionEventsChannel.send(
                        MessageBuilder.withPayload(inboundEvent).build(),
                    )
                }.build()
        consumer.start()
        return consumer
    }
}
