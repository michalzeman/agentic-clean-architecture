package mz.shared.connector.redis

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.boot.health.contributor.Status
import org.springframework.data.redis.connection.RedisConnection
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory

class RedisHealthIndicatorTest {
    private fun factoryReturning(ping: String): LettuceConnectionFactory {
        val connection = mock(RedisConnection::class.java)
        `when`(connection.ping()).thenReturn(ping)
        val factory = mock(LettuceConnectionFactory::class.java)
        `when`(factory.connection).thenReturn(connection)
        return factory
    }

    @Test
    fun `should report UP when ping returns PONG`() {
        assertThat(RedisHealthIndicator(factoryReturning("PONG")).health().status).isEqualTo(Status.UP)
    }

    @Test
    fun `should report DOWN when ping fails`() {
        assertThat(RedisHealthIndicator(factoryReturning("ERR")).health().status).isEqualTo(Status.DOWN)
    }
}
