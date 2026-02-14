package mz.bank.account.application.integration.inbound

import mz.bank.account.application.transaction.InboundBankTransactionEvent
import mz.bank.account.application.transaction.toCommand
import mz.bank.account.domain.BankAccountCommand
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.integration.dsl.IntegrationFlow
import org.springframework.integration.dsl.MessageChannels
import org.springframework.integration.jdbc.store.JdbcChannelMessageStore
import org.springframework.messaging.MessageChannel

@Configuration
class InboundBankTransactionEventConf(
    @param:Value("\${application.identifier}") private val applicationIdentifier: String,
    private val jsonJdbcChannelMessageStore: JdbcChannelMessageStore,
) {
    /**
     * Channel for inbound transaction events from the bank-transaction service.
     * PostgreSQL-backed for persistence and reliability.
     * Uses JSON serialization for the InboundBankTransactionEvent.
     */
    @Bean
    fun inboundBankTransactionEventsChannel(): MessageChannel =
        MessageChannels
            .queue(
                "$applicationIdentifier.persistence.inbound-bank-transaction-events.channel",
                jsonJdbcChannelMessageStore,
                "$applicationIdentifier.persistence.inbound-bank-transaction-events.storage",
            ).apply { datatype(InboundBankTransactionEvent::class.java) }
            .getObject()

    @Bean
    fun inboundBankTransactionEventsFlow(
        inboundBankTransactionEventsChannel: MessageChannel,
        bankAccountCommandChannel: MessageChannel,
    ): IntegrationFlow =
        IntegrationFlow
            .from(inboundBankTransactionEventsChannel)
            .transform<InboundBankTransactionEvent, BankAccountCommand> { event -> event.toCommand() }
            .filter<BankAccountCommand> { it !is BankAccountCommand.NoOp }
            .channel(bankAccountCommandChannel)
            .get()
}
