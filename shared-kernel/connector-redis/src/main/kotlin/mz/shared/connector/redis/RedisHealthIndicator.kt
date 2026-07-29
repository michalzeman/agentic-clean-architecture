package mz.shared.connector.redis

import org.springframework.boot.health.contributor.AbstractHealthIndicator
import org.springframework.boot.health.contributor.Health
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory

class RedisHealthIndicator(
    private val connectionFactory: LettuceConnectionFactory,
) : AbstractHealthIndicator() {
    override fun doHealthCheck(builder: Health.Builder) {
        val pong = connectionFactory.connection.use { it.ping() }
        if (pong == "PONG") {
            builder.up()
        } else {
            builder.down().withDetail("ping", pong)
        }
    }
}
