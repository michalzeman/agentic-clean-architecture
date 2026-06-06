package mz.shared.connector.redis

import mz.shared.domain.LockProvider
import org.redisson.api.RedissonClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.RedisSerializationContext
import org.springframework.data.redis.serializer.RedisSerializer
import org.springframework.integration.redis.util.RedisLockRegistry
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule

@AutoConfiguration
class DataPlatformRedisConfiguration(
    @param:Value("\${spring.data.redis.host}") val host: String,
    @param:Value("\${spring.data.redis.port}") val port: Int,
    @param:Value("\${application.lock:lock}") val lockRegistryName: String,
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
        val reactiveRedisTemplate: ReactiveRedisTemplate<String, String> =
            ReactiveRedisTemplate<String, String>(lettuceConnectionFactory, RedisSerializationContext.string())

        return reactiveRedisTemplate
    }

    @Bean
    fun reactiveStringRedisTemplate(redisConnectionFactory: LettuceConnectionFactory): ReactiveStringRedisTemplate =
        ReactiveStringRedisTemplate(redisConnectionFactory)

    @Bean
    fun redisLockRegistry(redisConnectionFactory: LettuceConnectionFactory): RedisLockRegistry =
        RedisLockRegistry(redisConnectionFactory, lockRegistryName)

    @Bean
    fun lockProvider(redisson: RedissonClient): LockProvider = RedisLockProvider(redisson)

    @Bean
    fun objectMapper(): JsonMapper = JsonMapper.builder().addModule(KotlinModule.Builder().build()).build()
}
