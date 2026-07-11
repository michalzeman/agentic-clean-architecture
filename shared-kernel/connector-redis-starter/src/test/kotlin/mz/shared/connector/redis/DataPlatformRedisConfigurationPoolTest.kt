package mz.shared.connector.redis

import org.apache.commons.pool2.impl.GenericObjectPoolConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Test
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration
import java.time.Duration

class DataPlatformRedisConfigurationPoolTest {
    @Test
    fun `should configure lettuce pool from constructor params not hardcoded values`() {
        val config =
            DataPlatformRedisConfiguration(
                lockRegistryName = "test-lock",
                host = "localhost",
                port = 6379,
                maxActive = 5,
                maxIdle = 3,
                minIdle = 1,
                maxWait = Duration.ofMillis(1000),
            )

        val factory = config.redisConnectionFactory()

        // Verify pooling is enabled
        val clientConfigField = LettuceConnectionFactory::class.java.getDeclaredField("clientConfiguration")
        clientConfigField.isAccessible = true
        val clientConfig = clientConfigField.get(factory)
        assertThat(clientConfig).isInstanceOf(LettucePoolingClientConfiguration::class.java)

        // Verify pool values match constructor params, not hardcoded defaults
        val poolConfigField = clientConfig.javaClass.getDeclaredField("poolConfig")
        poolConfigField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val poolConfig = poolConfigField.get(clientConfig) as GenericObjectPoolConfig<*>

        assertAll(
            { assertThat(poolConfig.maxTotal).isEqualTo(5) },
            { assertThat(poolConfig.maxIdle).isEqualTo(3) },
            { assertThat(poolConfig.minIdle).isEqualTo(1) },
            { assertThat(poolConfig.maxWaitMillis).isEqualTo(1000L) },
        )
    }
}
