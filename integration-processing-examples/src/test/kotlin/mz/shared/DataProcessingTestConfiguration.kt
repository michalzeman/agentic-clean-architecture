package mz.shared

import com.fasterxml.jackson.databind.ObjectMapper
import mz.shared.connector.redis.RedisSteamConsumerBuilder
import mz.shared.connector.redis.RedisStreamConsumer
import mz.shared.domain.JobDocument
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.connection.stream.Consumer
import org.springframework.integration.channel.PublishSubscribeChannel
import org.springframework.integration.dsl.MessageChannels
import org.springframework.integration.redis.store.RedisChannelMessageStore
import org.springframework.messaging.MessageChannel

@TestConfiguration
@ComponentScan("mz.integration.messaging.**")
class DataProcessingTestConfiguration(
    val redisConnectionFactory: LettuceConnectionFactory,
    val objectMapper: ObjectMapper,
) {
    @Bean
    fun testOutboundRedisStreamChannel(): PublishSubscribeChannel = PublishSubscribeChannel()

    @Bean
    fun jobDocumentInboundChannelTest(redisMessageJsonStore: RedisChannelMessageStore) =
        MessageChannels.queue(
            "job-document-inbound-test-channel",
            redisMessageJsonStore,
            "job-document-inbound-test-storage",
        )

    @Bean
    fun jobDocumentStorageChannelTest(redisMessageJsonStore: RedisChannelMessageStore) =
        MessageChannels
            .queue(
                "job-document-storage-test-channel",
                redisMessageJsonStore,
                "job-document-storage-test-storage",
            ).apply { datatype(JobDocument::class.java) }

    @Bean
    fun redisMessageProtoStore(lettuceConnectionFactory: LettuceConnectionFactory): RedisChannelMessageStore {
        val store = RedisChannelMessageStore(lettuceConnectionFactory)
        return store
    }

    @Bean
    fun jobModelProtoTestChannel(redisMessageProtoStore: RedisChannelMessageStore) =
        MessageChannels.queue(
            "job-model-proto-test-channel",
            redisMessageProtoStore,
            "job-model-proto-test-storage",
        )

    @Bean
    fun redisStreamConsumeTest(jobDocumentInboundChannelTest: MessageChannel): RedisStreamConsumer<JobDocument, JobDocument> {
        val consumerGroup = Consumer.from("my-group-test", "my-consumer-test")

        val builder =
            RedisSteamConsumerBuilder<JobDocument, JobDocument>(
                TEST_DATA_PROCESSING_STREAM,
                jobDocumentInboundChannelTest,
                consumerGroup.group,
                consumerGroup.name,
                JobDocument::class.java,
                redisConnectionFactory,
                objectMapper,
            )

        return builder.build()
    }
}
