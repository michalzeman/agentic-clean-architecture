package mz.bank.account.adapter.nats.jetstream.config

import kotlinx.coroutines.runBlocking
import mz.bank.account.adapter.nats.jetstream.BankAccountJetStreamProperties
import mz.bank.account.adapter.nats.jetstream.mapper.BankAccountEventJetStreamMessageConverter
import mz.bank.account.adapter.nats.jetstream.mapper.BankAccountEventToJetStreamMapper
import mz.bank.account.adapter.nats.jetstream.outbound.JetStreamBankAccountEventsPublisher
import mz.bank.account.contract.proto.BankAccountEvent
import mz.shared.connector.nats.NatsConnectionFactory
import mz.shared.connector.nats.jetstream.producer.DefaultJetStreamProducer
import mz.shared.connector.nats.jetstream.producer.JetStreamProducer
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.integration.annotation.ServiceActivator
import org.springframework.stereotype.Component
import mz.bank.account.domain.BankAccountEvent as DomainBankAccountEvent

/**
 * Configuration for outbound JetStream publishing.
 * Creates the producer bean and integration flow.
 */
@Configuration
@Profile("nats-jetstream")
@EnableConfigurationProperties(BankAccountJetStreamProperties::class)
class OutboundJetStreamConfiguration(
    private val properties: BankAccountJetStreamProperties,
) {
    @Bean
    fun jetStreamBankAccountProducer(
        connectionFactory: NatsConnectionFactory,
        bankAccountEventJetStreamConverter: BankAccountEventJetStreamMessageConverter,
    ): JetStreamProducer<BankAccountEvent> =
        DefaultJetStreamProducer(
            streamName = properties.bankAccountEventsStream,
            connectionFactory = connectionFactory,
            converter = bankAccountEventJetStreamConverter,
        )

    @Bean
    fun jetStreamBankAccountEventsPublisher(
        jetStreamBankAccountProducer: JetStreamProducer<BankAccountEvent>,
        mapper: BankAccountEventToJetStreamMapper,
    ): JetStreamBankAccountEventsPublisher =
        JetStreamBankAccountEventsPublisher(
            jetStreamProducer = jetStreamBankAccountProducer,
            mapper = mapper,
            properties = properties,
        )

    /**
     * ServiceActivator for routing outbound bank account domain events to JetStream.
     * Defined as a separate component to avoid circular dependency with the publisher bean.
     */
    @Component
    @Profile("nats-jetstream")
    class OutboundEventHandler(
        private val publisher: JetStreamBankAccountEventsPublisher,
    ) {
        @ServiceActivator(inputChannel = "outboundBankAccountJetStreamChannel")
        fun handleOutboundEvent(event: DomainBankAccountEvent) {
            runBlocking {
                publisher.publish(event)
            }
        }
    }
}
