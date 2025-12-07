package mz.bank.account.adapter.redis.stream.wiring

import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.hubspot.jackson.datatype.protobuf.ProtobufModule
import mz.bank.account.adapter.redis.stream.BankAccountEventMapper
import mz.bank.account.adapter.redis.stream.BankAccountRedisStreamProperties
import mz.shared.connector.redis.RedisStreamPublisher
import mz.shared.connector.redis.json.RedisMapRecordJsonSerializer
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.integration.channel.PublishSubscribeChannel
import org.springframework.integration.dsl.IntegrationFlow
import org.springframework.integration.support.json.JacksonJsonUtils
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
    private val properties: BankAccountRedisStreamProperties,
    private val reactiveRedisTemplate: ReactiveRedisTemplate<String, *>,
) {
    @Bean
    fun redisMapRecordProtobufSerializer(): RedisMapRecordJsonSerializer {
        val objectMapper =
            JacksonJsonUtils.messagingAwareMapper(
                "mz",
                "java.math",
                "org.springframework.data.redis.connection.stream",
                "kotlin.collections",
            )
        objectMapper
            .registerModule(KotlinModule.Builder().build())
            .registerModule(ProtobufModule())
        return RedisMapRecordJsonSerializer(objectMapper)
    }

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
