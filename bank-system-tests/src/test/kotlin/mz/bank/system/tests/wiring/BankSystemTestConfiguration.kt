package mz.bank.system.tests.wiring

import mz.bank.account.contract.proto.BankAccountEvent
import mz.bank.system.tests.ServiceHealthChecker
import mz.bank.transaction.contract.proto.BankTransactionEvent
import mz.shared.connector.nats.NatsConnectionFactory
import mz.shared.connector.nats.jetstream.consumer.JetStreamConsumer
import mz.shared.connector.nats.jetstream.consumer.JetStreamConsumerBuilder
import mz.shared.connector.nats.jetstream.message.JetStreamMessageConverter
import mz.shared.connector.redis.RedisSteamConsumerBuilder
import mz.shared.connector.redis.RedisStreamConsumer
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.integration.dsl.MessageChannels
import org.springframework.integration.dsl.QueueChannelSpec
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.support.MessageBuilder
import org.springframework.web.reactive.function.client.WebClient
import tools.jackson.databind.ObjectMapper

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

    /**
     * Gets the bank account NATS JetStream properties from the nested adapter structure.
     */
    val adapterNatsJetStreamBankAccount: BankAccountNatsJetStreamProperties
        get() = adapter.natsJetstream.bankAccount

    /**
     * Gets the bank transaction NATS JetStream properties from the nested adapter structure.
     */
    val adapterNatsJetStreamBankTransaction: BankTransactionNatsJetStreamProperties
        get() = adapter.natsJetstream.bankTransaction
}

/**
 * Top-level adapter properties container.
 */
data class AdapterProperties(
    val redisStreams: RedisStreamsProperties,
    val natsJetstream: NatsJetstreamProperties,
)

/**
 * Redis streams configuration container.
 */
data class RedisStreamsProperties(
    val bankAccount: BankAccountRedisStreamProperties,
    val bankTransaction: BankTransactionRedisStreamProperties,
)

/**
 * NATS JetStream configuration container.
 */
data class NatsJetstreamProperties(
    val bankAccount: BankAccountNatsJetStreamProperties,
    val bankTransaction: BankTransactionNatsJetStreamProperties,
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
 * Bank account NATS JetStream specific properties.
 */
data class BankAccountNatsJetStreamProperties(
    val eventsStream: String,
    val eventsSubject: String,
    val consumerName: String,
)

/**
 * Bank transaction NATS JetStream specific properties.
 */
data class BankTransactionNatsJetStreamProperties(
    val eventsStream: String,
    val eventsSubject: String,
    val consumerName: String,
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

    // ─── Redis channels ───────────────────────────────────────────────────────

    @Bean
    @ConditionalOnProperty(prefix = "nats.jetstream", name = ["enabled"], havingValue = "false", matchIfMissing = true)
    fun bankAccountEventsStreamChannel(): QueueChannelSpec = MessageChannels.queue()

    @Bean
    @ConditionalOnProperty(prefix = "nats.jetstream", name = ["enabled"], havingValue = "false", matchIfMissing = true)
    fun bankTransactionEventsStreamChannel(): QueueChannelSpec = MessageChannels.queue()

    /**
     * Redis stream consumer for bank account events.
     * Consumes AccountCreated events from the bank-account service stream.
     */
    @Bean
    @ConditionalOnProperty(prefix = "nats.jetstream", name = ["enabled"], havingValue = "false", matchIfMissing = true)
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
    @ConditionalOnProperty(prefix = "nats.jetstream", name = ["enabled"], havingValue = "false", matchIfMissing = true)
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

    // ─── NATS JetStream channels (active only when nats.jetstream.enabled=true) ──

    @Bean
    @ConditionalOnProperty(prefix = "nats.jetstream", name = ["enabled"], havingValue = "true")
    fun bankAccountEventsNatsStreamChannel(): QueueChannelSpec = MessageChannels.queue()

    @Bean
    @ConditionalOnProperty(prefix = "nats.jetstream", name = ["enabled"], havingValue = "true")
    fun bankTransactionEventsNatsStreamChannel(): QueueChannelSpec = MessageChannels.queue()

    /**
     * JetStream message converter for BankAccountEvent protobuf messages.
     */
    @Bean
    @ConditionalOnProperty(prefix = "nats.jetstream", name = ["enabled"], havingValue = "true")
    fun bankAccountEventJetStreamConverter(
        @Qualifier("protobufObjectMapper") objectMapper: ObjectMapper,
    ): JetStreamMessageConverter<BankAccountEvent> =
        object : JetStreamMessageConverter<BankAccountEvent> {
            override val contentType: String = "application/x-protobuf"

            override fun serialize(payload: BankAccountEvent): ByteArray = objectMapper.writeValueAsBytes(payload)

            override fun deserialize(
                data: ByteArray,
                subject: String,
                headers: Map<String, List<String>>,
            ): BankAccountEvent = objectMapper.readValue(data, BankAccountEvent::class.java)
        }

    /**
     * JetStream message converter for BankTransactionEvent protobuf messages.
     */
    @Bean
    @ConditionalOnProperty(prefix = "nats.jetstream", name = ["enabled"], havingValue = "true")
    fun bankTransactionEventJetStreamConverter(
        @Qualifier("protobufObjectMapper") objectMapper: ObjectMapper,
    ): JetStreamMessageConverter<BankTransactionEvent> =
        object : JetStreamMessageConverter<BankTransactionEvent> {
            override val contentType: String = "application/x-protobuf"

            override fun serialize(payload: BankTransactionEvent): ByteArray = objectMapper.writeValueAsBytes(payload)

            override fun deserialize(
                data: ByteArray,
                subject: String,
                headers: Map<String, List<String>>,
            ): BankTransactionEvent = objectMapper.readValue(data, BankTransactionEvent::class.java)
        }

    /**
     * NATS JetStream consumer for bank account events.
     * Active only when nats.jetstream.enabled=true.
     */
    @Bean
    @ConditionalOnProperty(prefix = "nats.jetstream", name = ["enabled"], havingValue = "true")
    fun bankAccountEventsNatsConsumer(
        connectionFactory: NatsConnectionFactory,
        bankAccountEventJetStreamConverter: JetStreamMessageConverter<BankAccountEvent>,
        bankAccountEventsNatsStreamChannel: MessageChannel,
    ): JetStreamConsumer<BankAccountEvent> {
        val consumer =
            JetStreamConsumerBuilder<BankAccountEvent>(
                streamName = testProperties.adapterNatsJetStreamBankAccount.eventsStream,
                subject = testProperties.adapterNatsJetStreamBankAccount.eventsSubject,
                consumerName = testProperties.adapterNatsJetStreamBankAccount.consumerName,
            ).withConnectionFactory(connectionFactory)
                .withConverter(bankAccountEventJetStreamConverter)
                .withMessageHandler { message ->
                    bankAccountEventsNatsStreamChannel.send(
                        MessageBuilder.withPayload(message.payload).build(),
                    )
                }.build()
        return consumer
    }

    /**
     * NATS JetStream consumer for bank transaction events.
     * Active only when nats.jetstream.enabled=true.
     */
    @Bean
    @ConditionalOnProperty(prefix = "nats.jetstream", name = ["enabled"], havingValue = "true")
    fun bankTransactionEventsNatsConsumer(
        connectionFactory: NatsConnectionFactory,
        bankTransactionEventJetStreamConverter: JetStreamMessageConverter<BankTransactionEvent>,
        bankTransactionEventsNatsStreamChannel: MessageChannel,
    ): JetStreamConsumer<BankTransactionEvent> {
        val consumer =
            JetStreamConsumerBuilder<BankTransactionEvent>(
                streamName = testProperties.adapterNatsJetStreamBankTransaction.eventsStream,
                subject = testProperties.adapterNatsJetStreamBankTransaction.eventsSubject,
                consumerName = testProperties.adapterNatsJetStreamBankTransaction.consumerName,
            ).withConnectionFactory(connectionFactory)
                .withConverter(bankTransactionEventJetStreamConverter)
                .withMessageHandler { message ->
                    bankTransactionEventsNatsStreamChannel.send(
                        MessageBuilder.withPayload(message.payload).build(),
                    )
                }.build()
        return consumer
    }
}
