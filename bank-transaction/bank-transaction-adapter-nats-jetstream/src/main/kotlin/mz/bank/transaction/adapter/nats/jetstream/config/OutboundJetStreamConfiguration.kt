package mz.bank.transaction.adapter.nats.jetstream.config

import kotlinx.coroutines.runBlocking
import mz.bank.transaction.adapter.nats.jetstream.BankTransactionJetStreamProperties
import mz.bank.transaction.adapter.nats.jetstream.mapper.BankTransactionEventJetStreamMessageConverter
import mz.bank.transaction.adapter.nats.jetstream.mapper.BankTransactionEventToJetStreamMapper
import mz.bank.transaction.adapter.nats.jetstream.outbound.JetStreamBankTransactionEventsPublisher
import mz.bank.transaction.contract.proto.BankTransactionEvent
import mz.shared.connector.nats.NatsConnectionFactory
import mz.shared.connector.nats.jetstream.producer.DefaultJetStreamProducer
import mz.shared.connector.nats.jetstream.producer.JetStreamProducer
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.integration.annotation.ServiceActivator
import org.springframework.integration.channel.PublishSubscribeChannel
import org.springframework.integration.dsl.IntegrationFlow
import org.springframework.stereotype.Component
import mz.bank.transaction.domain.BankTransactionEvent as DomainBankTransactionEvent

/**
 * Configuration for outbound JetStream publishing of bank transaction events.
 */
@Configuration
@Profile("nats-jetstream")
@EnableConfigurationProperties(BankTransactionJetStreamProperties::class)
class OutboundJetStreamConfiguration(
    private val properties: BankTransactionJetStreamProperties,
) {
    @Bean
    fun jetStreamBankTransactionProducer(
        connectionFactory: NatsConnectionFactory,
        bankTransactionEventJetStreamConverter: BankTransactionEventJetStreamMessageConverter,
    ): JetStreamProducer<BankTransactionEvent> =
        DefaultJetStreamProducer(
            streamName = properties.bankTransactionEventsStream,
            connectionFactory = connectionFactory,
            converter = bankTransactionEventJetStreamConverter,
        )

    @Bean
    fun jetStreamBankTransactionEventsPublisher(
        jetStreamBankTransactionProducer: JetStreamProducer<BankTransactionEvent>,
        mapper: BankTransactionEventToJetStreamMapper,
    ): JetStreamBankTransactionEventsPublisher =
        JetStreamBankTransactionEventsPublisher(
            jetStreamProducer = jetStreamBankTransactionProducer,
            mapper = mapper,
            properties = properties,
        )

    /**
     * Bridges domain events publish-subscribe channel to JetStream outbound channel.
     * This flow connects the application layer's domain event publication to the JetStream adapter.
     */
    @Bean
    fun outboundBankTransactionJetStreamFlow(
        bankTransactionDomainEventsPublishSubscribeChannel: PublishSubscribeChannel,
    ): IntegrationFlow =
        IntegrationFlow
            .from(bankTransactionDomainEventsPublishSubscribeChannel)
            .channel("outboundBankTransactionJetStreamChannel")
            .get()

    /**
     * ServiceActivator for routing outbound bank transaction domain events to JetStream.
     * Defined as a separate component to avoid circular dependency with the publisher bean.
     */
    @Component
    @Profile("nats-jetstream")
    class OutboundEventHandler(
        private val publisher: JetStreamBankTransactionEventsPublisher,
    ) {
        @ServiceActivator(inputChannel = "outboundBankTransactionJetStreamChannel")
        fun handleOutboundEvent(event: DomainBankTransactionEvent) {
            runBlocking {
                publisher.publish(event)
            }
        }
    }
}
