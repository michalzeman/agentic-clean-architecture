package mz.bank.transaction.adapter.redis.stream.wiring

import com.fasterxml.jackson.databind.ObjectMapper
import mz.bank.transaction.adapter.redis.stream.BankTransactionEventMapper
import mz.bank.transaction.adapter.redis.stream.BankTransactionRedisStreamProperties
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
import org.springframework.integration.jdbc.store.JdbcChannelMessageStore
import org.springframework.messaging.MessageChannel
import mz.bank.transaction.contract.proto.BankTransactionEvent as ProtoBankTransactionEvent
import mz.bank.transaction.domain.BankTransactionEvent as DomainBankTransactionEvent

/**
 * Configuration for outbound Redis stream publishing of bank transaction events.
 * Handles transformation from domain events to protobuf events and publishing to Redis stream.
 */
@Configuration
@EnableConfigurationProperties(BankTransactionRedisStreamProperties::class)
class OutboundBankTransactionRedisStreamConfiguration(
    @param:Value("\${application.identifier}")
    private val applicationIdentifier: String,
    private val properties: BankTransactionRedisStreamProperties,
    private val reactiveRedisTemplate: ReactiveRedisTemplate<String, *>,
    private val protoJdbcChannelMessageStore: JdbcChannelMessageStore,
) {
    @Bean
    fun redisMapRecordProtobufSerializer(
        @Qualifier("protobufObjectMapper") objectMapper: ObjectMapper,
    ): RedisMapRecordJsonSerializer = RedisMapRecordJsonSerializer(objectMapper)

    @Bean
    fun bankTransactionEventsRedisStreamPublisher(
        redisMapRecordProtobufSerializer: RedisMapRecordJsonSerializer,
    ): RedisStreamPublisher<ProtoBankTransactionEvent> =
        RedisStreamPublisher(
            streamKey = properties.bankTransactionEventsStream,
            reactiveRedisTemplate = reactiveRedisTemplate,
            redisMapRecordJsonSerializer = redisMapRecordProtobufSerializer,
        )

    /**
     * PostgreSQL-backed channel for protobuf events to be published to Redis stream.
     * Uses protobuf serialization for efficient storage and transmission.
     */
    @Bean
    fun outboundBankTransactionRedisStreamChannel(): MessageChannel =
        MessageChannels
            .queue(
                "$applicationIdentifier.persistence.outbound-bank-transaction-redis-stream.channel",
                protoJdbcChannelMessageStore,
                "$applicationIdentifier.persistence.outbound-bank-transaction-redis-stream.storage",
            ).apply { datatype(ProtoBankTransactionEvent::class.java) }
            .getObject()

    /**
     * Integration flow that transforms domain events to protobuf and sends to Redis stream channel.
     * Subscribes to PublishSubscribe channel and transforms domain events to protobuf events.
     */
    @Bean
    fun outboundBankTransactionRedisStreamFlow(
        bankTransactionDomainEventsPublishSubscribeChannel: PublishSubscribeChannel,
        outboundBankTransactionRedisStreamChannel: MessageChannel,
    ): IntegrationFlow =
        IntegrationFlow
            .from(bankTransactionDomainEventsPublishSubscribeChannel)
            .log()
            .transform(DomainBankTransactionEvent::class.java) { event: DomainBankTransactionEvent ->
                BankTransactionEventMapper.toProto(event)
            }.channel(outboundBankTransactionRedisStreamChannel)
            .get()
}
