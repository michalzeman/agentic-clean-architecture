package mz.bank.account.application.integration.command

import mz.bank.account.domain.BankAccountCommand
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.integration.dsl.MessageChannels
import org.springframework.integration.redis.store.RedisChannelMessageStore
import org.springframework.messaging.MessageChannel

/**
 * Configuration for bank account command channel.
 *
 * Provides a Redis-backed persistent queue for processing BankAccountCommand asynchronously.
 * Commands published to this channel are consumed by BankAccountCommandHandler.
 */
@Configuration
class BankAccountCommandChannelConf(
    @param:Value("\${application.identifier}") private val applicationIdentifier: String,
    private val jsonRedisChannelMessageStore: RedisChannelMessageStore,
) {
    /**
     * Channel for bank account commands.
     * Redis-backed for persistence and reliability.
     * Uses JSON serialization for BankAccountCommand.
     */
    @Bean
    fun bankAccountCommandChannel(): MessageChannel =
        MessageChannels
            .queue(
                "$applicationIdentifier.persistence.bank-account-commands.channel",
                jsonRedisChannelMessageStore,
                "$applicationIdentifier.persistence.bank-account-commands.storage",
            ).apply { datatype(BankAccountCommand::class.java) }
            .getObject()
}
