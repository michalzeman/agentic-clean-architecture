package mz.bank.account.adapter.redis.stream.wiring

import com.fasterxml.jackson.databind.ObjectMapper
import mz.bank.account.adapter.redis.stream.BankAccountEventMapper
import mz.bank.account.adapter.redis.stream.BankAccountRedisStreamProperties
import mz.shared.connector.redis.RedisStreamPublisher
import mz.shared.connector.redis.json.RedisMapRecordJsonSerializer
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.integration.channel.PublishSubscribeChannel
import org.springframework.integration.dsl.IntegrationFlow
import org.springframework.integration.dsl.MessageChannels
import org.springframework.integration.redis.store.RedisChannelMessageStore
import org.springframework.messaging.MessageChannel
import mz.bank.account.contract.proto.BankAccountEvent as ProtoBankAccountEvent
import mz.bank.account.domain.BankAccountEvent as DomainBankAccountEvent

/**
 * Configuration for outbound Redis stream publishing of bank account events.
 * Handles transformation from domain events to protobuf events and publishing to Redis stream.
 */
@Configuration
@EnableConfigurationProperties(BankAccountRedisStreamProperties::class)
class OutboundBankAccountRedisStreamConfiguration(
    @param:Value("\${application.identifier}")
    private val applicationIdentifier: String,
    private val properties: BankAccountRedisStreamProperties,
    private val reactiveRedisTemplate: ReactiveRedisTemplate<String, *>,
    private val protoRedisChannelMessageStore: RedisChannelMessageStore,
    @param:Qualifier("protobufObjectMapper") private val protobufObjectMapper: ObjectMapper,
) {
    @Bean
    fun redisMapRecordProtobufSerializer(): RedisMapRecordJsonSerializer = RedisMapRecordJsonSerializer(protobufObjectMapper)

    @Bean
    fun bankAccountEventsRedisStreamPublisher(
        redisMapRecordProtobufSerializer: RedisMapRecordJsonSerializer,
    ): RedisStreamPublisher<ProtoBankAccountEvent> =
        RedisStreamPublisher(
            streamKey = properties.bankAccountEventsStream,
            reactiveRedisTemplate = reactiveRedisTemplate,
            redisMapRecordJsonSerializer = redisMapRecordProtobufSerializer,
        )

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

    /**
     * Integration flow that transforms domain events to protobuf and sends to Redis stream channel.
     * Subscribes to PublishSubscribe channel and transforms domain events to protobuf events.
     */
    @Bean
    fun outboundBankAccountRedisStreamFlow(
        bankAccountDomainEventsPublishSubscribeChannel: PublishSubscribeChannel,
        outboundBankAccountRedisStreamChannel: MessageChannel,
    ): IntegrationFlow =
        IntegrationFlow
            .from(bankAccountDomainEventsPublishSubscribeChannel)
            .log()
            .transform(DomainBankAccountEvent::class.java) { event: DomainBankAccountEvent ->
                BankAccountEventMapper.toProto(event)
            }.channel(outboundBankAccountRedisStreamChannel)
            .get()
}
