package mz.bank.account.adapter.nats.jetstream.config

import kotlinx.coroutines.runBlocking
import mz.bank.account.adapter.nats.jetstream.BankAccountJetStreamProperties
import mz.bank.account.adapter.nats.jetstream.mapper.BankAccountEventToJetStreamMapper
import mz.bank.account.adapter.nats.jetstream.outbound.JetStreamBankAccountEventsPublisher
import mz.bank.account.contract.proto.BankAccountEvent
import mz.shared.connector.nats.jetstream.producer.JetStreamProducer
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.integration.annotation.ServiceActivator
import mz.bank.account.domain.BankAccountEvent as DomainBankAccountEvent

/**
 * Configuration for outbound JetStream publishing.
 * Creates the producer bean and integration flow.
 */
@Configuration
@Profile("nats-jetstream")
@EnableConfigurationProperties(BankAccountJetStreamProperties::class)
class OutboundJetStreamConfiguration(
    @Qualifier("natsConnectionFactory") private val connectionFactory: Any,
    private val properties: BankAccountJetStreamProperties,
) {
    @Bean
    fun jetStreamBankAccountEventsPublisher(
        jetStreamProducer: JetStreamProducer<BankAccountEvent>,
        mapper: BankAccountEventToJetStreamMapper,
    ): JetStreamBankAccountEventsPublisher =
        JetStreamBankAccountEventsPublisher(
            jetStreamProducer = jetStreamProducer,
            mapper = mapper,
            properties = properties,
        )

    @ServiceActivator(inputChannel = "outboundBankAccountJetStreamChannel")
    fun handleOutboundEvent(
        event: DomainBankAccountEvent,
        publisher: JetStreamBankAccountEventsPublisher,
    ) {
        runBlocking {
            publisher.publish(event)
        }
    }
}
