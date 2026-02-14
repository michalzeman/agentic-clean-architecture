package mz.bank.transaction.application.integration.command

import mz.bank.transaction.domain.BankTransactionCommand
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.integration.dsl.MessageChannels
import org.springframework.integration.jdbc.store.JdbcChannelMessageStore
import org.springframework.messaging.MessageChannel

/**
 * Configuration for bank transaction command channel.
 *
 * Provides a PostgreSQL-backed persistent queue for processing BankTransactionCommand asynchronously.
 * Commands published to this channel are consumed by BankTransactionCommandHandler.
 */
@Configuration
class BankTransactionCommandChannelConf(
    @param:Value("\${application.identifier}") private val applicationIdentifier: String,
    private val jsonJdbcChannelMessageStore: JdbcChannelMessageStore,
) {
    /**
     * Channel for bank transaction commands.
     * PostgreSQL-backed for persistence and reliability.
     * Uses JSON serialization for BankTransactionCommand.
     */
    @Bean
    fun bankTransactionCommandChannel(): MessageChannel =
        MessageChannels
            .queue(
                "$applicationIdentifier.persistence.bank-transaction-commands.channel",
                jsonJdbcChannelMessageStore,
                "$applicationIdentifier.persistence.bank-transaction-commands.storage",
            ).apply { datatype(BankTransactionCommand::class.java) }
            .getObject()
}
