package mz.shared.connector.nats.jetstream.consumer

import io.nats.client.Connection
import io.nats.client.JetStream
import io.nats.client.api.StorageType
import io.nats.client.api.StreamConfiguration
import kotlinx.coroutines.runBlocking
import mz.shared.connector.nats.DefaultNatsConnectionFactory
import mz.shared.connector.nats.NatsConnectionFactory
import mz.shared.connector.nats.NatsConnectionProperties
import mz.shared.connector.nats.jetstream.message.JetStreamMessageConversionException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.awaitility.kotlin.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JetStreamConsumerTest {
    @Container
    private val natsContainer: GenericContainer<*> =
        GenericContainer("nats:2.10.7")
            .withExposedPorts(4222)
            .withCommand("--config", "/config/jetstream.conf")
            .withClasspathResourceMapping("nats-jetstream.conf", "/config/jetstream.conf", BindMode.READ_ONLY)
            .waitingFor(Wait.forLogMessage(".*Server is ready.*", 1))

    private val streamName = "TEST_STREAM"
    private val subjectFilter = "test.subject.>"

    // Factories created per test — tracked for proper cleanup
    private val openFactories = CopyOnWriteArrayList<DefaultNatsConnectionFactory>()

    @AfterEach
    fun closeFactories() {
        openFactories.forEach { factory ->
            runCatching { factory.close() }
        }
        openFactories.clear()
    }

    private fun getNatsUrl(): String = "nats://${natsContainer.host}:${natsContainer.firstMappedPort}"

    private fun createConnectionFactory(): NatsConnectionFactory {
        val properties = NatsConnectionProperties(serverUrl = getNatsUrl())
        return DefaultNatsConnectionFactory(properties).also { openFactories.add(it) }
    }

    private fun createConnection(): Connection = createConnectionFactory().getConnection()

    private fun createStream(connection: Connection) {
        val jsm = connection.jetStreamManagement()

        try {
            jsm.deleteStream(streamName)
        } catch (_: Exception) {
        }

        val config =
            StreamConfiguration
                .builder()
                .name(streamName)
                .subjects("test.subject.*")
                .storageType(StorageType.Memory)
                .build()

        jsm.addStream(config)
    }

    private fun publishMessage(
        connection: Connection,
        subject: String,
        payload: String,
    ) {
        val jetStream: JetStream = connection.jetStream()
        val data = payload.toByteArray(StandardCharsets.UTF_8)
        jetStream.publish(subject, data)
    }

    @Test
    fun `should consume published messages`() {
        val connection = createConnection()
        createStream(connection)

        val receivedMessages = CopyOnWriteArrayList<JetStreamMessage<String>>()

        val consumer = buildConsumer(receivedMessages)

        publishMessage(connection, "test.subject.one", "Hello World")

        await.atMost(Duration.ofSeconds(10)).until { receivedMessages.isNotEmpty() }

        assertThat(receivedMessages).hasSize(1)
        assertThat(receivedMessages[0].payload).isEqualTo("Hello World")
        assertThat(receivedMessages[0].subject).isEqualTo("test.subject.one")

        (consumer as? DefaultJetStreamConsumer)?.stopConsuming()
    }

    @Test
    fun `should acknowledge messages`() {
        val connection = createConnection()
        createStream(connection)

        val receivedMessages = CopyOnWriteArrayList<JetStreamMessage<String>>()

        val consumer = buildConsumer(receivedMessages, "test-consumer-ack")

        publishMessage(connection, "test.subject.two", "Message to ack")

        await.atMost(Duration.ofSeconds(10)).until { receivedMessages.isNotEmpty() }

        assertThat(receivedMessages).hasSize(1)
        runBlocking {
            consumer.acknowledge(receivedMessages[0])
        }

        (consumer as? DefaultJetStreamConsumer)?.stopConsuming()
    }

    @Test
    fun `should negatively acknowledge messages`() {
        val connection = createConnection()
        createStream(connection)

        val receivedMessages = CopyOnWriteArrayList<JetStreamMessage<String>>()

        val consumer = buildConsumer(receivedMessages, "test-consumer-nak")

        publishMessage(connection, "test.subject.three", "Message to nak")

        await.atMost(Duration.ofSeconds(10)).until { receivedMessages.isNotEmpty() }

        assertThat(receivedMessages).hasSize(1)
        runBlocking {
            consumer.negativeAcknowledge(receivedMessages[0])
        }

        (consumer as? DefaultJetStreamConsumer)?.stopConsuming()
    }

    @Test
    fun `should throw exception when acknowledging message without raw message`() {
        val connection = createConnection()
        createStream(connection)

        val receivedMessages = CopyOnWriteArrayList<JetStreamMessage<String>>()

        val consumer = buildConsumer(receivedMessages, "test-consumer-no-raw")

        publishMessage(connection, "test.subject.four", "Test message")

        await.atMost(Duration.ofSeconds(10)).until { receivedMessages.isNotEmpty() }

        assertThat(receivedMessages).hasSize(1)

        val messageWithoutRaw = receivedMessages[0].copy(rawMessage = null)

        assertThatThrownBy {
            runBlocking {
                consumer.acknowledge(messageWithoutRaw)
            }
        }.isInstanceOf(JetStreamConsumeException::class.java)
            .hasMessageContaining("Cannot acknowledge message")

        (consumer as? DefaultJetStreamConsumer)?.stopConsuming()
    }

    @Test
    fun `should throw exception on deserialization error and process the next message`() {
        val connection = createConnection()
        createStream(connection)

        val receivedMessages = CopyOnWriteArrayList<JetStreamMessage<String>>()

        val consumer =
            JetStreamConsumerBuilder<String>(
                streamName = streamName,
                subject = subjectFilter,
                consumerName = "test-consumer-error",
            ).withConnectionFactory(createConnectionFactory())
                .withConverter(ThrowingConverter())
                .withMessageHandler { message ->
                    receivedMessages.add(message)
                }.withBatchSize(10)
                .build()

        publishMessage(connection, "test.subject.five", "Invalid data")

        publishMessage(connection, "test.subject.six", "Valid data")

        // Wait a reasonable time; deserialization errors should not deliver to the handler
        await.atMost(Duration.ofSeconds(10)).during(Duration.ofSeconds(2)).until { receivedMessages.isNotEmpty() }

        assertThat(receivedMessages).hasSize(1)

        (consumer as? DefaultJetStreamConsumer)?.stopConsuming()
    }

    private fun buildConsumer(
        receivedMessages: MutableList<JetStreamMessage<String>>,
        consumerName: String = "test-consumer",
    ): JetStreamConsumer<String> =
        JetStreamConsumerBuilder<String>(
            streamName = streamName,
            subject = subjectFilter,
            consumerName = consumerName,
        ).withConnectionFactory(createConnectionFactory())
            .withConverter(SimpleStringConverter())
            .withMessageHandler { message ->
                receivedMessages.add(message)
            }.withBatchSize(10)
            .build()

    private class SimpleStringConverter : mz.shared.connector.nats.jetstream.message.JetStreamMessageConverter<String> {
        override fun serialize(payload: String): ByteArray = payload.toByteArray(StandardCharsets.UTF_8)

        override fun deserialize(
            data: ByteArray,
            subject: String,
            headers: Map<String, List<String>>,
        ): String = String(data, StandardCharsets.UTF_8)

        override val contentType: String = "text/plain"
    }

    private class ThrowingConverter : mz.shared.connector.nats.jetstream.message.JetStreamMessageConverter<String> {
        override fun serialize(payload: String): ByteArray = payload.toByteArray(StandardCharsets.UTF_8)

        override fun deserialize(
            data: ByteArray,
            subject: String,
            headers: Map<String, List<String>>,
        ): String {
            val payload = String(data, StandardCharsets.UTF_8)
            return if (payload.contains("Invalid data")) {
                throw JetStreamMessageConversionException("Simulated deserialization error")
            } else {
                payload
            }
        }

        override val contentType: String = "text/plain"
    }
}
