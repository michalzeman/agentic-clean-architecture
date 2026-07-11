package mz.shared.connector.redis

import io.lettuce.core.api.StatefulConnection
import mz.shared.domain.LockProvider
import org.apache.commons.pool2.impl.GenericObjectPoolConfig
import org.redisson.api.RedissonClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.RedisSerializationContext
import org.springframework.data.redis.serializer.RedisSerializer
import org.springframework.integration.redis.util.RedisLockRegistry
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import java.time.Duration

@AutoConfiguration
class DataPlatformRedisConfiguration(
    @param:Value("\${application.lock:lock}") val lockRegistryName: String,
    @param:Value("\${spring.data.redis.host}") val host: String,
    @param:Value("\${spring.data.redis.port}") val port: Int,
    @param:Value("\${spring.data.redis.lettuce.pool.max-active:16}") val maxActive: Int,
    @param:Value("\${spring.data.redis.lettuce.pool.max-idle:8}") val maxIdle: Int,
    @param:Value("\${spring.data.redis.lettuce.pool.min-idle:4}") val minIdle: Int,
    @param:Value("\${spring.data.redis.lettuce.pool.max-wait:-1ms}") val maxWait: Duration,
) {
    @Bean
    fun redisConnectionFactory(): LettuceConnectionFactory {
        val poolConfig = GenericObjectPoolConfig<StatefulConnection<*, *>>()
        poolConfig.maxTotal = maxActive
        poolConfig.maxIdle = maxIdle
        poolConfig.minIdle = minIdle
        poolConfig.setMaxWait(maxWait)
        val clientConfig =
            LettucePoolingClientConfiguration
                .builder()
                .poolConfig(poolConfig)
                .build()
        return LettuceConnectionFactory(RedisStandaloneConfiguration(host, port), clientConfig)
    }

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
