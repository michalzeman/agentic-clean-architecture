package mz.shared.connector.redis.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.TaskExecutor
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.ThreadPoolExecutor

/**
 * Auto-configuration for Redis Stream message channel executor.
 * Provides a configurable TaskExecutor for Spring Integration ServiceActivators.
 */
@Configuration
@EnableConfigurationProperties(RedisIntegrationProperties::class)
class RedisStreamExecutorConfig(
    private val properties: RedisIntegrationProperties,
) {
    /**
     * Creates a TaskExecutor for Redis Stream message processing.
     * Only created if no bean named "redisStreamTaskExecutor" exists and
     * app.integration.redis.enabled is true (default).
     */
    @Bean
    @ConditionalOnMissingBean(name = ["redisStreamTaskExecutor"])
    @ConditionalOnProperty(
        prefix = "app.integration.redis",
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = true,
    )
    fun redisStreamTaskExecutor(): TaskExecutor =
        ThreadPoolTaskExecutor().apply {
            corePoolSize = properties.executor.corePoolSize
            maxPoolSize = properties.executor.maxPoolSize
            setRejectedExecutionHandler(ThreadPoolExecutor.CallerRunsPolicy())
            setThreadNamePrefix(properties.executor.threadNamePrefix)
            initialize()
        }
}
