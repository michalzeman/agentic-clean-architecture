package mz.bank.system.tests.wiring

import com.fasterxml.jackson.databind.ObjectMapper
import mz.bank.account.contract.proto.BankAccountEvent
import mz.bank.system.tests.ServiceHealthChecker
import mz.bank.transaction.contract.proto.BankTransactionEvent
import mz.shared.connector.redis.RedisSteamConsumerBuilder
import mz.shared.connector.redis.RedisStreamConsumer
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.integration.dsl.MessageChannels
import org.springframework.integration.dsl.QueueChannelSpec
import org.springframework.messaging.MessageChannel
import org.springframework.web.reactive.function.client.WebClient

/**
 * Configuration properties for bank system tests.
 */
@ConfigurationProperties(prefix = "bank-system-tests")
data class TestProperties(
    val accountServiceUrl: String,
    val transactionServiceUrl: String,
    val healthEndpoint: String,
    val adapter: AdapterProperties,
) {
    /**
     * Gets the bank account Redis stream properties from the nested adapter structure.
     */
    val adapterRedisStreamsBankAccount: BankAccountRedisStreamProperties
        get() = adapter.redisStreams.bankAccount

    /**
     * Gets the bank account Redis stream properties from the nested adapter structure.
     */
    val adapterRedisStreamsBankTransaction: BankTransactionRedisStreamProperties
        get() = adapter.redisStreams.bankTransaction
}

/**
 * Top-level adapter properties container.
 */
data class AdapterProperties(
    val redisStreams: RedisStreamsProperties,
)

/**
 * Redis streams configuration container.
 */
data class RedisStreamsProperties(
    val bankAccount: BankAccountRedisStreamProperties,
    val bankTransaction: BankTransactionRedisStreamProperties,
)

/**
 * Bank account Redis stream specific properties.
 */
data class BankAccountRedisStreamProperties(
    val eventsStream: String,
    val eventsConsumerGroup: String,
    val eventsConsumerName: String,
)

/**
 * Bank transaction Redis stream specific properties.
 */
data class BankTransactionRedisStreamProperties(
    val eventsStream: String,
    val eventsConsumerGroup: String,
    val eventsConsumerName: String,
)

/**
 * Spring configuration for bank system tests.
 * Provides WebClient beans for both services and health checkers.
 */
@Configuration
@EnableAutoConfiguration
@EnableConfigurationProperties(TestProperties::class)
class BankSystemTestConfiguration(
    private val testProperties: TestProperties,
    private val lettuceConnectionFactory: LettuceConnectionFactory,
) {
    /**
     * WebClient for bank-account service.
     */
    @Bean
    fun accountServiceWebClient(): WebClient =
        WebClient
            .builder()
            .baseUrl(testProperties.accountServiceUrl)
            .build()

    /**
     * WebClient for bank-transaction service.
     */
    @Bean
    fun transactionServiceWebClient(): WebClient =
        WebClient
            .builder()
            .baseUrl(testProperties.transactionServiceUrl)
            .build()

    /**
     * Health checker for bank-account service.
     */
    @Bean
    fun accountServiceHealthChecker(): ServiceHealthChecker =
        ServiceHealthChecker(
            webClient = accountServiceWebClient(),
            healthEndpoint = testProperties.healthEndpoint,
            serviceName = "bank-account-service",
        )

    /**
     * Health checker for bank-transaction service.
     */
    @Bean
    fun transactionServiceHealthChecker(): ServiceHealthChecker =
        ServiceHealthChecker(
            webClient = transactionServiceWebClient(),
            healthEndpoint = testProperties.healthEndpoint,
            serviceName = "bank-transaction-service",
        )

    @Bean
    fun bankAccountEventsStreamChannel(): QueueChannelSpec = MessageChannels.queue()

    @Bean
    fun bankTransactionEventsStreamChannel(): QueueChannelSpec = MessageChannels.queue()

    /**
     * Redis stream consumer for bank account events.
     * Consumes AccountCreated events from the bank-account service stream.
     */
    @Bean
    fun bankAccountEventsRedisStreamConsumer(
        bankAccountEventsStreamChannel: MessageChannel,
        @Qualifier("protobufObjectMapper") protobufObjectMapper: ObjectMapper,
    ): RedisStreamConsumer<BankAccountEvent, BankAccountEvent> =
        RedisSteamConsumerBuilder<BankAccountEvent, BankAccountEvent>(
            streamKey = testProperties.adapterRedisStreamsBankAccount.eventsStream,
            channel = bankAccountEventsStreamChannel,
            consumerGroup = testProperties.adapterRedisStreamsBankAccount.eventsConsumerGroup,
            consumerName = testProperties.adapterRedisStreamsBankAccount.eventsConsumerName,
            type = BankAccountEvent::class.java,
            redisConnectionFactory = lettuceConnectionFactory,
            objectMapper = protobufObjectMapper,
        ).build()

    /**
     * Redis stream consumer for bank transaction events.
     * Consumes protobuf BankTransactionEvent events from the bank-transaction service stream.
     */
    @Bean
    fun bankTransactionEventsRedisStreamConsumer(
        bankTransactionEventsStreamChannel: MessageChannel,
        @Qualifier("protobufObjectMapper") protobufObjectMapper: ObjectMapper,
    ): RedisStreamConsumer<BankTransactionEvent, BankTransactionEvent> =
        RedisSteamConsumerBuilder<BankTransactionEvent, BankTransactionEvent>(
            streamKey = testProperties.adapterRedisStreamsBankTransaction.eventsStream,
            channel = bankTransactionEventsStreamChannel,
            consumerGroup = testProperties.adapterRedisStreamsBankTransaction.eventsConsumerGroup,
            consumerName = testProperties.adapterRedisStreamsBankTransaction.eventsConsumerName,
            type = BankTransactionEvent::class.java,
            redisConnectionFactory = lettuceConnectionFactory,
            objectMapper = protobufObjectMapper,
        ).build()
}
