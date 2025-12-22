package mz.bank.transaction.application.integration

import mz.bank.transaction.domain.BankTransactionEvent
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.integration.channel.PublishSubscribeChannel
import org.springframework.integration.dsl.IntegrationFlow
import org.springframework.integration.dsl.MessageChannels
import org.springframework.integration.redis.store.RedisChannelMessageStore
import org.springframework.messaging.MessageChannel

/**
 * Integration configuration for outbound bank transaction domain events.
 *
 * Architecture:
 * 1. [bankTransactionDomainEventsChannel] - Redis-backed persistent queue for domain events
 * 2. [bankTransactionDomainEventsPublishSubscribeChannel] - Fanout channel for multiple consumers
 *
 * Flow: Domain Events → Persistent Queue → PublishSubscribe → Consumer Queues (defined in adapters)
 *
 * Note: This configuration only handles domain events. Protobuf transformation and Redis stream
 * publishing are handled by the adapter-redis-stream module to maintain dependency inversion.
 */
@Configuration
class BankTransactionEventsIntegrationConf(
    @param:Value("\${application.identifier}")
    private val applicationIdentifier: String,
    private val jsonRedisChannelMessageStore: RedisChannelMessageStore,
) {
    /**
     * Primary channel for domain events published by BankTransactionDomainEventListener.
     * Redis-backed for persistence and reliability.
     */
    @Bean
    fun bankTransactionDomainEventsChannel(): MessageChannel =
        MessageChannels
            .queue(
                "$applicationIdentifier.persistence.bank-transaction-domain-events.channel",
                jsonRedisChannelMessageStore,
                "$applicationIdentifier.persistence.bank-transaction-domain-events.storage",
            ).apply { datatype(BankTransactionEvent::class.java) }
            .getObject()

    /**
     * PublishSubscribe channel for fanout to multiple consumers.
     * Enables future scalability where multiple consumers can process the same events.
     */
    @Bean
    fun bankTransactionDomainEventsPublishSubscribeChannel(): PublishSubscribeChannel =
        MessageChannels
            .publishSubscribe()
            .apply { datatype(BankTransactionEvent::class.java) }
            .getObject()

    /**
     * Integration flow connecting the persistent queue to the PublishSubscribe channel.
     */
    @Bean
    fun bankTransactionDomainEventsFlow(
        bankTransactionDomainEventsChannel: MessageChannel,
        bankTransactionDomainEventsPublishSubscribeChannel: PublishSubscribeChannel,
    ): IntegrationFlow =
        IntegrationFlow
            .from(bankTransactionDomainEventsChannel)
            .log()
            .channel(bankTransactionDomainEventsPublishSubscribeChannel)
            .get()
}
