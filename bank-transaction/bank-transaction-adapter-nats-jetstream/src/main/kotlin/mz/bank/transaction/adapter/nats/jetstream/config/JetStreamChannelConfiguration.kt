package mz.bank.transaction.adapter.nats.jetstream.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.integration.dsl.MessageChannels
import org.springframework.messaging.MessageChannel
import mz.bank.transaction.domain.BankTransactionEvent as DomainBankTransactionEvent

/**
 * Configuration for JetStream message channels.
 * Provides outbound channel for publishing domain events to JetStream.
 */
@Configuration
@Profile("nats-jetstream")
class JetStreamChannelConfiguration {
    /**
     * Channel for outbound bank transaction events to be published to JetStream.
     */
    @Bean
    fun outboundBankTransactionJetStreamChannel(
        @Value("\${application.identifier}") applicationIdentifier: String,
    ): MessageChannel =
        MessageChannels
            .publishSubscribe(
                "$applicationIdentifier.messaging.outbound-bank-transaction-jetstream.channel",
            ).apply {
                datatype(DomainBankTransactionEvent::class.java)
            }.getObject()
}
