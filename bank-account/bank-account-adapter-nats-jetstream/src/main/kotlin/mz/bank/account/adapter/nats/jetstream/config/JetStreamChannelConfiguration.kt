package mz.bank.account.adapter.nats.jetstream.config

import mz.bank.account.adapter.nats.jetstream.BankAccountJetStreamProperties
import mz.bank.transaction.contract.proto.BankTransactionEvent
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.integration.dsl.MessageChannels
import org.springframework.integration.jdbc.store.JdbcChannelMessageStore
import org.springframework.messaging.MessageChannel
import mz.bank.account.domain.BankAccountEvent as DomainBankAccountEvent

/**
 * Configuration for JetStream message channels.
 * Provides outbound channel for publishing domain events to JetStream.
 */
@Configuration
@Profile("nats-jetstream")
@EnableConfigurationProperties(BankAccountJetStreamProperties::class)
class JetStreamChannelConfiguration(
    @param:Qualifier("protoJdbcChannelMessageStore") private val channelMessageStore: JdbcChannelMessageStore,
    private val properties: BankAccountJetStreamProperties,
) {
    /**
     * Channel for outbound bank account events to be published to JetStream.
     */
    @Bean
    fun outboundBankAccountJetStreamChannel(
        @Qualifier("applicationIdentifier") applicationIdentifier: String,
    ): MessageChannel =
        MessageChannels
            .publishSubscribe(
                "$applicationIdentifier.messaging.outbound-bank-account-jetstream.channel",
            ).apply {
                datatype(DomainBankAccountEvent::class.java)
            }.getObject()

    /**
     * Channel for inbound bank transaction events from JetStream.
     */
    @Bean
    fun inboundBankTransactionJetStreamChannel(
        @Qualifier("applicationIdentifier") applicationIdentifier: String,
    ): MessageChannel =
        MessageChannels
            .queue(
                "$applicationIdentifier.messaging.inbound-bank-transaction-jetstream.channel",
                channelMessageStore,
                "$applicationIdentifier.messaging.inbound-bank-transaction-jetstream.storage",
            ).apply {
                datatype(BankTransactionEvent::class.java)
            }.getObject()
}
