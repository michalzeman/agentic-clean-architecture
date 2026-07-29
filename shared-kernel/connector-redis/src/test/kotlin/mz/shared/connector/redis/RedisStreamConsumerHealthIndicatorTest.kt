package mz.shared.connector.redis

import kotlinx.coroutines.flow.emptyFlow
import mz.shared.connector.redis.json.RedisMapRecordJsonSerializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.health.contributor.Status
import org.springframework.data.redis.connection.stream.Consumer
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.messaging.MessageChannel

class RedisStreamConsumerHealthIndicatorTest {
    private class ListProvider<T : Any>(
        private val items: List<T>,
    ) : ObjectProvider<T> {
        override fun getObject(): T = items.first()

        override fun iterator(): MutableIterator<T> = items.toMutableList().iterator()
    }

    @Suppress("UNCHECKED_CAST")
    private fun newConsumer(name: String): RedisStreamConsumer<com.google.protobuf.Any, Any> =
        RedisStreamConsumer(
            channel = mock(MessageChannel::class.java),
            serializer = mock(RedisMapRecordJsonSerializer::class.java),
            type = com.google.protobuf.Any::class.java,
            recordStream = emptyFlow(),
            errorChannel = null,
            mapper = null,
            reactiveRedisTemplate = mock(ReactiveRedisTemplate::class.java) as ReactiveRedisTemplate<String, *>,
            consumer = Consumer.from("test-group", name),
            lockProvider = null,
        )

    @Test
    fun `should report UP when all consumers running`() {
        val running = newConsumer("c1").also { it.start() }
        val indicator = RedisStreamConsumerHealthIndicator(ListProvider(listOf(running)))
        val health = indicator.health()
        assertThat(health.status).isEqualTo(Status.UP)
        assertThat(health.details["consumerCount"]).isEqualTo(1)
        running.stop()
    }

    @Test
    fun `should report DOWN with stopped consumer names`() {
        val stopped = newConsumer("c2")
        val indicator = RedisStreamConsumerHealthIndicator(ListProvider(listOf(stopped)))
        val health = indicator.health()
        assertThat(health.status).isEqualTo(Status.DOWN)
        assertThat(health.details["stoppedConsumers"]).isEqualTo(listOf("c2"))
    }
}
