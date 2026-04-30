package mz.bank.transaction.adapter.nats.jetstream.config

import com.fasterxml.jackson.databind.ObjectMapper
import mz.bank.account.contract.proto.BankAccountEvent
import mz.bank.transaction.adapter.nats.jetstream.BankTransactionJetStreamProperties
import mz.bank.transaction.adapter.nats.jetstream.inbound.toInboundEvent
import mz.bank.transaction.adapter.nats.jetstream.mapper.BankAccountEventJetStreamMessageConverter
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
 * Sets up the consumer and message converter for handling bank account events.
 */
@Configuration
@Profile("nats-jetstream")
@EnableConfigurationProperties(BankTransactionJetStreamProperties::class)
class InboundJetStreamConfiguration(
    private val properties: BankTransactionJetStreamProperties,
) {
    @Bean
    fun bankAccountEventJetStreamConverter(
        @Qualifier("protobufObjectMapper") objectMapper: ObjectMapper,
    ): JetStreamMessageConverter<BankAccountEvent> = BankAccountEventJetStreamMessageConverter(objectMapper)

    @Bean
    fun jetStreamConsumer(
        connectionFactory: NatsConnectionFactory,
        bankAccountEventJetStreamConverter: JetStreamMessageConverter<BankAccountEvent>,
        inboundBankAccountEventsChannel: MessageChannel,
    ): JetStreamConsumer<BankAccountEvent> {
        val consumer =
            JetStreamConsumerBuilder<BankAccountEvent>(
                streamName = properties.bankAccountEventsStream,
                subject = properties.bankAccountEventsSubject,
                consumerName = properties.consumerName,
            ).withConnectionFactory(connectionFactory)
                .withConverter(bankAccountEventJetStreamConverter)
                .withMessageHandler { message ->
                    val inboundEvent = message.payload.toInboundEvent()
                    inboundBankAccountEventsChannel.send(
                        MessageBuilder.withPayload(inboundEvent).build(),
                    )
                }.build()
        consumer.start()
        return consumer
    }
}
