package mz.shared.connector.nats.jetstream.consumer

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.health.contributor.Status

class JetStreamConsumerHealthIndicatorTest {
    private class FakeConsumer(
        override val consumerName: String,
        private val running: Boolean,
    ) : JetStreamConsumer<Any> {
        override val streamName: String = "stream"
        override val subjectFilter: String = "subject"

        override suspend fun acknowledge(message: JetStreamMessage<Any>) = Unit

        override suspend fun negativeAcknowledge(message: JetStreamMessage<Any>) = Unit

        override fun start() = Unit

        override fun stop() = Unit

        override fun isRunning(): Boolean = running
    }

    private class ListProvider<T : Any>(
        private val items: List<T>,
    ) : ObjectProvider<T> {
        override fun getObject(): T = items.first()

        override fun iterator(): MutableIterator<T> = items.toMutableList().iterator()
    }

    @Test
    fun `should report UP when all consumers running`() {
        val indicator = JetStreamConsumerHealthIndicator(ListProvider(listOf(FakeConsumer("c1", true))))
        val health = indicator.health()
        assertThat(health.status).isEqualTo(Status.UP)
        assertThat(health.details["consumers"]).isEqualTo(listOf("c1"))
    }

    @Test
    fun `should report DOWN with stopped and running consumer names`() {
        val indicator =
            JetStreamConsumerHealthIndicator(
                ListProvider(listOf(FakeConsumer("up-c", true), FakeConsumer("down-c", false))),
            )
        val health = indicator.health()
        assertThat(health.status).isEqualTo(Status.DOWN)
        assertThat(health.details["stoppedConsumers"]).isEqualTo(listOf("down-c"))
        assertThat(health.details["runningConsumers"]).isEqualTo(listOf("up-c"))
    }
}
