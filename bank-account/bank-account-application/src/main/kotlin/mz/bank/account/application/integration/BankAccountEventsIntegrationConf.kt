package mz.bank.account.application.integration

import mz.bank.account.domain.BankAccountEvent
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.integration.channel.PublishSubscribeChannel
import org.springframework.integration.dsl.IntegrationFlow
import org.springframework.integration.dsl.MessageChannels
import org.springframework.integration.redis.store.RedisChannelMessageStore
import org.springframework.messaging.MessageChannel
import mz.bank.account.contract.proto.BankAccountEvent as ProtoBankAccountEvent

/**
 * Integration configuration for outbound bank account domain events.
 *
 * Architecture:
 * 1. [bankAccountDomainEventsChannel] - Redis-backed persistent queue for domain events
 * 2. [bankAccountDomainEventsPublishSubscribeChannel] - Fanout channel for multiple consumers
 * 3. [outboundBankAccountRedisStreamChannel] - Redis-backed queue for Redis stream publishing (protobuf)
 *
 * Flow: Domain Events → Persistent Queue → PublishSubscribe → Consumer Queues → Redis Stream
 */
@Configuration
class BankAccountEventsIntegrationConf(
    @param:Value("\${application.identifier}")
    private val applicationIdentifier: String,
    private val jsonRedisChannelMessageStore: RedisChannelMessageStore,
    private val protoRedisChannelMessageStore: RedisChannelMessageStore,
) {
    /**
     * Primary channel for domain events published by BankAccountDomainEventListener.
     * Redis-backed for persistence and reliability.
     */
    @Bean
    fun bankAccountDomainEventsChannel(): MessageChannel =
        MessageChannels
            .queue(
                "$applicationIdentifier.persistence.bank-account-domain-events.channel",
                jsonRedisChannelMessageStore,
                "$applicationIdentifier.persistence.bank-account-domain-events.storage",
            ).apply { datatype(BankAccountEvent::class.java) }
            .getObject()

    /**
     * PublishSubscribe channel for fanout to multiple consumers.
     * Enables future scalability where multiple consumers can process the same events.
     */
    @Bean
    fun bankAccountDomainEventsPublishSubscribeChannel(): PublishSubscribeChannel =
        MessageChannels
            .publishSubscribe()
            .apply { datatype(BankAccountEvent::class.java) }
            .getObject()

    /**
     * Integration flow connecting the persistent queue to the PublishSubscribe channel.
     */
    @Bean
    fun bankAccountDomainEventsFlow(
        bankAccountDomainEventsChannel: MessageChannel,
        bankAccountDomainEventsPublishSubscribeChannel: PublishSubscribeChannel,
    ): IntegrationFlow =
        IntegrationFlow
            .from(bankAccountDomainEventsChannel)
            .log()
            .channel(bankAccountDomainEventsPublishSubscribeChannel)
            .get()

    /**
     * Redis-backed channel for protobuf events to be published to Redis stream.
     * Uses protobuf serialization for efficient storage and transmission.
     */
    @Bean
    fun outboundBankAccountRedisStreamChannel(): MessageChannel =
        MessageChannels
            .queue(
                "$applicationIdentifier.persistence.outbound-bank-account-redis-stream.channel",
                protoRedisChannelMessageStore,
                "$applicationIdentifier.persistence.outbound-bank-account-redis-stream.storage",
            ).apply { datatype(ProtoBankAccountEvent::class.java) }
            .getObject()
}
