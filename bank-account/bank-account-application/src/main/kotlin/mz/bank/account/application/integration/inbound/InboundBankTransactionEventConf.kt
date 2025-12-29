package mz.bank.account.application.integration.inbound

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.integration.dsl.MessageChannels
import org.springframework.integration.redis.store.RedisChannelMessageStore
import org.springframework.messaging.MessageChannel

@Configuration
class InboundBankTransactionEventConf(
    @param:Value("\${application.identifier}") private val applicationIdentifier: String,
    private val jsonRedisChannelMessageStore: RedisChannelMessageStore,
) {
    /**
     * Channel for inbound transaction events from the bank-transaction service.
     * Redis-backed for persistence and reliability.
     * Uses JSON serialization for the InboundBankTransactionEvent.
     */
    @Bean
    fun inboundBankTransactionEventsChannel(): MessageChannel =
        MessageChannels
            .queue(
                "$applicationIdentifier.persistence.inbound-bank-transaction-events.channel",
                jsonRedisChannelMessageStore,
                "$applicationIdentifier.persistence.inbound-bank-transaction-events.storage",
            ).apply { datatype(InboundBankTransactionEvent::class.java) }
            .getObject()
}
