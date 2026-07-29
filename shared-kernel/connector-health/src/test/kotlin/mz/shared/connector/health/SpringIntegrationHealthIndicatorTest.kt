package mz.shared.connector.health

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.health.contributor.Status
import org.springframework.integration.endpoint.AbstractEndpoint

class SpringIntegrationHealthIndicatorTest {
    private class FakeEndpoint : AbstractEndpoint() {
        override fun doStart() = Unit

        override fun doStop() = Unit

        override fun getComponentType(): String = "fake-endpoint"
    }

    private fun endpoint(
        name: String,
        started: Boolean,
    ): AbstractEndpoint =
        FakeEndpoint().also {
            it.beanName = name
            if (started) it.start()
        }

    private class ListProvider<T : Any>(
        private val items: List<T>,
    ) : ObjectProvider<T> {
        override fun getObject(): T = items.first()

        override fun iterator(): MutableIterator<T> = items.toMutableList().iterator()
    }

    @Test
    fun `should report UP when all endpoints running`() {
        val indicator =
            SpringIntegrationHealthIndicator(
                ListProvider(listOf(endpoint("flow-1", true), endpoint("flow-2", true))),
            )
        val health = indicator.health()
        assertThat(health.status).isEqualTo(Status.UP)
        assertThat(health.details["endpointCount"]).isEqualTo(2)
    }

    @Test
    fun `should report DOWN listing stopped endpoint bean names`() {
        val indicator =
            SpringIntegrationHealthIndicator(
                ListProvider(listOf(endpoint("flow-1", true), endpoint("flow-2", false))),
            )
        val health = indicator.health()
        assertThat(health.status).isEqualTo(Status.DOWN)
        assertThat(health.details["stoppedEndpoints"]).isEqualTo(listOf("flow-2"))
    }
}
