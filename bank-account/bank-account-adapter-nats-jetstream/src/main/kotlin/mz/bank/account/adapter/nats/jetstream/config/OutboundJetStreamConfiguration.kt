package mz.bank.account.adapter.nats.jetstream.config

import mz.bank.account.adapter.nats.jetstream.BankAccountJetStreamProperties
import mz.bank.account.adapter.nats.jetstream.mapper.BankAccountEventJetStreamMessageConverter
import mz.bank.account.adapter.nats.jetstream.mapper.BankAccountEventToJetStreamMapper
import mz.bank.account.adapter.nats.jetstream.outbound.JetStreamBankAccountEventsPublisher
import mz.bank.account.contract.proto.BankAccountEvent
import mz.shared.connector.nats.NatsConnectionFactory
import mz.shared.connector.nats.jetstream.producer.DefaultJetStreamProducer
import mz.shared.connector.nats.jetstream.producer.JetStreamProducer
import mz.shared.connector.observability.TraceParent
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.integration.channel.PublishSubscribeChannel
import org.springframework.integration.dsl.IntegrationFlow
import org.springframework.integration.handler.LoggingHandler
import org.springframework.messaging.Message
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
        traceParent: TraceParent?,
    ): JetStreamProducer<BankAccountEvent> =
        DefaultJetStreamProducer(
            streamName = properties.bankAccountEventsStream,
            connectionFactory = connectionFactory,
            converter = bankAccountEventJetStreamConverter,
            traceParent = traceParent,
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
     * Bridges domain events publish-subscribe channel to JetStream outbound channel.
     * This flow connects the application layer's domain event publication to the JetStream adapter.
     */
    @Bean
    fun outboundBankAccountJetStreamFlow(bankAccountDomainEventsPublishSubscribeChannel: PublishSubscribeChannel): IntegrationFlow =
        IntegrationFlow
            .from(bankAccountDomainEventsPublishSubscribeChannel)
            .log<Message<*>>(LoggingHandler.Level.INFO, "mz.bank.account.tracing.outbound") { m ->
                val event = m.payload as DomainBankAccountEvent
                "[Outbound:Entering] - eventType=${event::class.simpleName}, aggregateId=${event.aggregateId.value}"
            }.channel("outboundBankAccountJetStreamChannel")
            .get()
}
