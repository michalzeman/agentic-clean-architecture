package mz.shared

import com.fasterxml.jackson.databind.ObjectMapper
import mz.shared.connector.redis.RedisSteamConsumerBuilder
import mz.shared.connector.redis.RedisStreamConsumer
import mz.shared.domain.JobDocument
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.connection.stream.Consumer
import org.springframework.integration.channel.QueueChannel

private const val MY_STREAM_KEY = "my-stream-key"

@Configuration
class RedisStreamConsumerConfiguration(
    val redisConnectionFactory: LettuceConnectionFactory,
    val objectMapper: ObjectMapper,
) {
    @Bean
    fun redisStreamReceiver(jobDocumentInboundChannel: QueueChannel): RedisStreamConsumer<JobDocument, JobDocument> {
        val consumerGroup = Consumer.from("my-group-1", "my-consumer-1")

        val builder =
            RedisSteamConsumerBuilder<JobDocument, JobDocument>(
                MY_STREAM_KEY,
                jobDocumentInboundChannel,
                consumerGroup.group,
                consumerGroup.name,
                JobDocument::class.java,
                redisConnectionFactory,
                objectMapper,
            )

        return builder.build()
    }
}
