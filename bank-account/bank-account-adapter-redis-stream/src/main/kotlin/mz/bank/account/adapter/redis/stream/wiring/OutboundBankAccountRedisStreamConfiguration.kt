package mz.bank.account.adapter.redis.stream.wiring

import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.hubspot.jackson.datatype.protobuf.ProtobufModule
import mz.bank.account.adapter.redis.stream.BankAccountRedisStreamProperties
import mz.bank.account.contract.proto.BankAccountEvent
import mz.shared.connector.redis.RedisStreamPublisher
import mz.shared.connector.redis.json.RedisMapRecordJsonSerializer
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.integration.support.json.JacksonJsonUtils

/**
 * Configuration for outbound Redis stream publishing of bank account events.
 */
@Configuration
@EnableConfigurationProperties(BankAccountRedisStreamProperties::class)
class OutboundBankAccountRedisStreamConfiguration(
    private val properties: BankAccountRedisStreamProperties,
    private val reactiveRedisTemplate: ReactiveRedisTemplate<String, *>,
) {
    @Bean
    fun redisMapRecordJsonSerializer(): RedisMapRecordJsonSerializer {
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
        redisMapRecordJsonSerializer: RedisMapRecordJsonSerializer,
    ): RedisStreamPublisher<BankAccountEvent> =
        RedisStreamPublisher(
            streamKey = properties.bankAccountEventsStream,
            reactiveRedisTemplate = reactiveRedisTemplate,
            redisMapRecordJsonSerializer = redisMapRecordJsonSerializer,
        )
}
