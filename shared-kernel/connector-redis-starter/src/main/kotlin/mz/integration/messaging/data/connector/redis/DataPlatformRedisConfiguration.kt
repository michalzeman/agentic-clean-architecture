package mz.integration.messaging.data.connector.redis

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.hubspot.jackson.datatype.protobuf.ProtobufModule
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.RedisSerializationContext
import org.springframework.data.redis.serializer.RedisSerializer

@AutoConfiguration
class DataPlatformRedisConfiguration(
    @param:Value("\${spring.data.redis.host}") val host: String,
    @param:Value("\${spring.data.redis.port}") val port: Int,
) {
    @Bean
    fun redisConnectionFactory(): LettuceConnectionFactory = LettuceConnectionFactory(RedisStandaloneConfiguration(host, port))

    @Bean
    fun redisTemplate(lettuceConnectionFactory: LettuceConnectionFactory): RedisTemplate<String, *> {
        val template = RedisTemplate<String, Any>()
        template.connectionFactory = lettuceConnectionFactory
        template.setDefaultSerializer(RedisSerializer.string())
        template.setEnableTransactionSupport(true)
        return template
    }

    @Bean
    fun reactiveRedisTemplate(lettuceConnectionFactory: LettuceConnectionFactory): ReactiveRedisTemplate<String, *> {
        val reactiveRedisTemplate: ReactiveRedisTemplate<String, *> =
            ReactiveRedisTemplate<String, String>(lettuceConnectionFactory, RedisSerializationContext.string())

        return reactiveRedisTemplate
    }

    @Bean
    fun reactiveStringRedisTemplate(redisConnectionFactory: LettuceConnectionFactory): ReactiveStringRedisTemplate =
        ReactiveStringRedisTemplate(redisConnectionFactory)

    @Bean
    fun jackson2ObjectMapperBuilderCustomizer(): Jackson2ObjectMapperBuilderCustomizer =
        Jackson2ObjectMapperBuilderCustomizer { builder ->
            builder.modules(
                listOf(
                    ProtobufModule(),
                    KotlinModule.Builder().build(),
                    JavaTimeModule(),
                ),
            )
        }
}
