package mz.bank.transaction.application.integration

import mz.shared.domain.AggregateId
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.integration.dsl.MessageChannels
import org.springframework.integration.redis.store.RedisChannelMessageStore
import org.springframework.messaging.MessageChannel

/**
 * Integration configuration for inbound bank account events.
 *
 * Architecture:
 * 1. [inboundBankAccountEventsChannel] - Redis-backed persistent queue for incoming account events
 *
 * Flow: Redis Stream Consumer → AccountCreatedEvent → Persistent Queue → Handler
 *
 * Note: This configuration handles events coming from the bank-account service.
 * The Redis stream consumer is configured in the adapter-redis-stream module.
 */
@Configuration
class InboundBankAccountEventsIntegrationConf(
    @param:Value("\${application.identifier}")
    private val applicationIdentifier: String,
    private val jsonRedisChannelMessageStore: RedisChannelMessageStore,
) {
    /**
     * Channel for inbound account events from the bank-account service.
     * Redis-backed for persistence and reliability.
     */
    @Bean
    fun inboundBankAccountEventsChannel(): MessageChannel =
        MessageChannels
            .queue(
                "$applicationIdentifier.persistence.inbound-bank-account-events.channel",
                jsonRedisChannelMessageStore,
                "$applicationIdentifier.persistence.inbound-bank-account-events.storage",
            ).apply { datatype(AccountCreatedEvent::class.java) }
            .getObject()
}

/**
 * Event representing that an account was created in the bank-account service.
 * This is a simple application event used for internal messaging.
 */
data class AccountCreatedEvent(
    val accountId: AggregateId,
)
