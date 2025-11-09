package mz.integration.messaging.data

import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.hubspot.jackson.datatype.protobuf.ProtobufModule
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.RedisSerializationContext
import org.springframework.data.redis.serializer.RedisSerializer
import org.springframework.data.redis.serializer.StringRedisSerializer
import org.springframework.integration.channel.QueueChannel
import org.springframework.integration.dsl.MessageChannels
import org.springframework.integration.redis.store.RedisChannelMessageStore
import org.springframework.integration.support.json.JacksonJsonUtils

@Configuration
class DataProcessingConfiguration {
    @Bean
    fun jsonRedisSerializationContext(): RedisSerializationContext<String, Any> =
        RedisSerializationContext
            .newSerializationContext<String, Any>(StringRedisSerializer())
            .value(genericJackson2JsonRedisSerializer())
            .build()

    @Bean
    fun genericJackson2JsonRedisSerializer(): GenericJackson2JsonRedisSerializer {
        val mapper =
            JacksonJsonUtils.messagingAwareMapper(
                "mz.integration.messaging",
                "org.springframework.data.redis.connection.stream",
                "kotlin.collections",
            )
        mapper
            .registerModule(KotlinModule.Builder().build())
            .registerModule(ProtobufModule())
        return GenericJackson2JsonRedisSerializer(mapper)
    }

    @Bean
    fun redisMessageJsonStore(connectionFactory: LettuceConnectionFactory): RedisChannelMessageStore {
        val store = RedisChannelMessageStore(connectionFactory)
        val serializer: RedisSerializer<Any?> = genericJackson2JsonRedisSerializer()
        store.setValueSerializer(serializer)
        return store
    }

    @Bean
    fun jobDocumentInboundChannel(redisMessageJsonStore: RedisChannelMessageStore) =
        MessageChannels.queue("job-document-inbound-channel", redisMessageJsonStore, "job-document-inbound-storage")

    @Bean
    fun requestReplayChannel() = QueueChannel()

    @Bean
    fun resultReplayChannel(redisMessageJsonStore: RedisChannelMessageStore) =
        MessageChannels.queue("job-result", redisMessageJsonStore, "result-message-store")

    @Bean
    fun outboundRedisStreamChannel() = MessageChannels.publishSubscribe("outboundRedisStreamChannel")
}
