package mz.bank.account.adapter.redis.stream.wiring

import com.fasterxml.jackson.databind.ObjectMapper
import mz.bank.account.adapter.redis.stream.BankAccountRedisStreamProperties
import mz.bank.account.adapter.redis.stream.inbound.toInboundEvent
import mz.bank.account.application.integration.inbound.InboundBankTransactionEvent
import mz.bank.transaction.contract.proto.BankTransactionEvent
import mz.shared.connector.redis.RedisSteamConsumerBuilder
import mz.shared.connector.redis.RedisStreamConsumer
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.messaging.MessageChannel

/**
 * Configuration for inbound Redis stream consumption of bank transaction events.
 * Handles consuming events from the bank-transaction service and routing them to a persistent channel.
 */
@Configuration
@EnableConfigurationProperties(BankAccountRedisStreamProperties::class)
class InboundBankTransactionRedisStreamConfiguration(
    private val properties: BankAccountRedisStreamProperties,
    private val lettuceConnectionFactory: LettuceConnectionFactory,
    @param:Qualifier("protobufObjectMapper") private val protobufObjectMapper: ObjectMapper,
) {
    /**
     * Redis stream consumer for bank transaction events.
     * Consumes protobuf BankTransactionEvent events from the bank-transaction service stream
     * and converts them to InboundBankTransactionEvent for internal processing.
     */
    @Bean
    fun bankTransactionEventsRedisStreamConsumer(
        inboundBankTransactionEventsChannel: MessageChannel,
    ): RedisStreamConsumer<BankTransactionEvent, InboundBankTransactionEvent> =
        RedisSteamConsumerBuilder<BankTransactionEvent, InboundBankTransactionEvent>(
            streamKey = properties.bankTransactionEventsStream,
            channel = inboundBankTransactionEventsChannel,
            consumerGroup = properties.bankTransactionEventsConsumerGroup,
            consumerName = properties.bankTransactionEventsConsumerName,
            type = BankTransactionEvent::class.java,
            redisConnectionFactory = lettuceConnectionFactory,
            objectMapper = protobufObjectMapper,
        ).withMapper { protoEvent: BankTransactionEvent ->
            protoEvent.toInboundEvent()
        }.build()
}
