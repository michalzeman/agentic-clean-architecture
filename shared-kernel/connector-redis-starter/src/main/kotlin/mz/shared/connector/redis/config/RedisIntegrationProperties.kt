package mz.shared.connector.redis.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuration properties for Redis Integration tuning.
 * Provides externalized configuration for thread pool executors and pollers
 * used by Spring Integration message channels.
 */
@ConfigurationProperties(prefix = "app.integration.redis")
data class RedisIntegrationProperties(
    /** Enable/disable Redis integration auto-configuration */
    val enabled: Boolean = true,
    /** Thread pool executor configuration for message channel processing */
    val executor: ExecutorProperties = ExecutorProperties(),
    /** Poller configuration for message channel polling */
    val poller: PollerProperties = PollerProperties(),
)

/**
 * Thread pool executor configuration properties.
 * Controls the TaskExecutor used by ServiceActivators and message handlers.
 */
data class ExecutorProperties(
    /** Core number of threads in the pool */
    val corePoolSize: Int = 10,
    /** Maximum number of threads in the pool */
    val maxPoolSize: Int = 50,
    /** Prefix for thread names (useful for debugging and monitoring) */
    val threadNamePrefix: String = "message-channel-",
)

/**
 * Poller configuration properties.
 * Controls how messages are polled from channels.
 */
data class PollerProperties(
    /** Fixed delay between poll operations in milliseconds */
    val fixedDelay: Long = 100,
    /** Maximum number of messages to process per poll */
    val maxMessagesPerPoll: Int = 10,
)
