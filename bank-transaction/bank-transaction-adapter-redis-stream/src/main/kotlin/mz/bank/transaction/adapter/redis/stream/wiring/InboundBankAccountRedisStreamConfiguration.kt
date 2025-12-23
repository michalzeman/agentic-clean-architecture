package mz.bank.transaction.adapter.redis.stream.wiring

import com.fasterxml.jackson.databind.ObjectMapper
import mz.bank.account.contract.proto.BankAccountEvent
import mz.bank.transaction.adapter.redis.stream.BankTransactionRedisStreamProperties
import mz.bank.transaction.adapter.redis.stream.inbound.BankAccountEventMapper
import mz.bank.transaction.application.integration.AccountCreatedEvent
import mz.shared.connector.redis.RedisSteamConsumerBuilder
import mz.shared.connector.redis.RedisStreamConsumer
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.messaging.MessageChannel

/**
 * Configuration for inbound Redis stream consumption of bank account events.
 * Handles consuming events from the bank-account service and transforming them to application events.
 */
@Configuration
@EnableConfigurationProperties(BankTransactionRedisStreamProperties::class)
class InboundBankAccountRedisStreamConfiguration(
    private val properties: BankTransactionRedisStreamProperties,
    private val lettuceConnectionFactory: LettuceConnectionFactory,
    @param:Qualifier("protobufObjectMapper") private val protobufObjectMapper: ObjectMapper,
) {
    /**
     * Redis stream consumer for bank account events.
     * Consumes AccountCreated events from the bank-account service stream.
     */
    @Bean
    fun bankAccountEventsRedisStreamConsumer(
        inboundBankAccountEventsChannel: MessageChannel,
    ): RedisStreamConsumer<BankAccountEvent, AccountCreatedEvent?> =
        RedisSteamConsumerBuilder<BankAccountEvent, AccountCreatedEvent?>(
            streamKey = properties.bankAccountEventsStream,
            channel = inboundBankAccountEventsChannel,
            consumerGroup = properties.bankAccountEventsConsumerGroup,
            consumerName = properties.bankAccountEventsConsumerName,
            type = BankAccountEvent::class.java,
            redisConnectionFactory = lettuceConnectionFactory,
            objectMapper = protobufObjectMapper,
        ).withMapper { protoEvent: BankAccountEvent ->
            BankAccountEventMapper.toAccountCreatedEvent(protoEvent)
        }.build()
}
