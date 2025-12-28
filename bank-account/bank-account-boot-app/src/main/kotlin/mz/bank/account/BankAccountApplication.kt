package mz.bank.account

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.hubspot.jackson.datatype.protobuf.ProtobufModule
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer
import org.springframework.integration.redis.store.RedisChannelMessageStore
import org.springframework.integration.support.json.JacksonJsonUtils

@SpringBootApplication
class BankAccountApplication {
    @Bean
    fun genericJackson2JsonRedisSerializer(): GenericJackson2JsonRedisSerializer {
        val mapper =
            JacksonJsonUtils.messagingAwareMapper(
                "mz", // project-specific package
                "java.math",
                "org.springframework.data.redis.connection.stream",
                "kotlin.collections",
            )
        mapper
            .registerModule(KotlinModule.Builder().build())
            .registerModule(ProtobufModule())
        return GenericJackson2JsonRedisSerializer(mapper)
    }

    @Bean
    fun jsonRedisChannelMessageStore(
        redisConnectionFactory: LettuceConnectionFactory,
        genericJackson2JsonRedisSerializer: GenericJackson2JsonRedisSerializer,
    ): RedisChannelMessageStore =
        RedisChannelMessageStore(redisConnectionFactory).apply {
            setValueSerializer(genericJackson2JsonRedisSerializer)
        }

    @Bean
    fun protoRedisChannelMessageStore(redisConnectionFactory: LettuceConnectionFactory): RedisChannelMessageStore =
        RedisChannelMessageStore(redisConnectionFactory) // uses default byte array serialization

    @Bean
    @Primary
    fun objectMapper(): ObjectMapper {
        val objectMapper = ObjectMapper()
        objectMapper.registerModule(KotlinModule.Builder().build())
        objectMapper.registerModule(JavaTimeModule())
        return objectMapper
    }
}

fun main(args: Array<String>) {
    runApplication<BankAccountApplication>(*args)
}
