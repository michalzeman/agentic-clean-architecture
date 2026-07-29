package mz.shared.connector.redis

import kotlinx.coroutines.flow.emptyFlow
import mz.shared.connector.redis.json.RedisMapRecordJsonSerializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.data.redis.connection.stream.Consumer
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.messaging.MessageChannel

class RedisStreamConsumerLifecycleTest {
    @Suppress("UNCHECKED_CAST")
    private fun newConsumer(): RedisStreamConsumer<com.google.protobuf.Any, Any> =
        RedisStreamConsumer(
            channel = mock(MessageChannel::class.java),
            serializer = mock(RedisMapRecordJsonSerializer::class.java),
            type = com.google.protobuf.Any::class.java,
            recordStream = emptyFlow(),
            errorChannel = null,
            mapper = null,
            reactiveRedisTemplate = mock(ReactiveRedisTemplate::class.java) as ReactiveRedisTemplate<String, *>,
            consumer = Consumer.from("test-group", "test-consumer"),
            lockProvider = null,
        )

    @Test
    fun `should not be running before start`() {
        assertThat(newConsumer().isRunning).isFalse()
    }

    @Test
    fun `should be running after start and stop on stop`() {
        val consumer = newConsumer()
        consumer.start()
        assertThat(consumer.isRunning).isTrue()
        consumer.stop()
        assertThat(consumer.isRunning).isFalse()
    }

    @Test
    fun `should start idempotently`() {
        val consumer = newConsumer()
        consumer.start()
        consumer.start()
        assertThat(consumer.isRunning).isTrue()
        consumer.stop()
    }

    @Test
    fun `should stop on destroy`() {
        val consumer = newConsumer()
        consumer.start()
        consumer.destroy()
        assertThat(consumer.isRunning).isFalse()
    }

    @Test
    fun `should restart consumption after stop and start`() {
        val consumer = newConsumer()
        consumer.start()
        consumer.stop()
        consumer.start()
        assertThat(consumer.isRunning).isTrue()
        consumer.stop()
        assertThat(consumer.isRunning).isFalse()
    }
}
