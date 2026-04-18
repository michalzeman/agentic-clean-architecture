package mz.shared.connector.nats.jetstream.starter

import io.nats.client.Connection
import io.nats.client.api.StorageType
import io.nats.client.api.StreamConfiguration
import mz.shared.connector.nats.DefaultNatsConnectionFactory
import mz.shared.connector.nats.NatsConnectionFactory
import mz.shared.connector.nats.NatsConnectionProperties
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.concurrent.CopyOnWriteArrayList

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JetStreamStreamInitializerTest {
    @Container
    private val natsContainer: GenericContainer<*> =
        GenericContainer("nats:2.10.7")
            .withExposedPorts(4222)
            .withCommand("--config", "/config/jetstream.conf")
            .withClasspathResourceMapping("nats-jetstream.conf", "/config/jetstream.conf", BindMode.READ_ONLY)
            .waitingFor(Wait.forLogMessage(".*Server is ready.*", 1))

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

    @Test
    fun `should create configured streams on initialization`() {
        val connection = createConnection()
        val jsm = connection.jetStreamManagement()

        // Verify streams don't exist yet
        assertThatThrownBy { jsm.getStreamInfo("test-stream-1") }
            .hasMessageContaining("stream not found")

        assertThatThrownBy { jsm.getStreamInfo("test-stream-2") }
            .hasMessageContaining("stream not found")

        // Create initializer with configured streams
        val streamConfig =
            mapOf(
                "test-stream-1" to
                    JetStreamProperties.StreamProperties(
                        enabled = true,
                        subjects = listOf("test.subject.one.*"),
                        storageType = "FILE",
                        retentionPolicy = "LIMITS",
                        discardPolicy = "OLD",
                        maxMessages = 10000,
                    ),
                "test-stream-2" to
                    JetStreamProperties.StreamProperties(
                        enabled = true,
                        subjects = listOf("test.subject.two.*"),
                        storageType = "FILE",
                        retentionPolicy = "LIMITS",
                        discardPolicy = "OLD",
                        maxMessages = 5000,
                    ),
            )
        val properties =
            JetStreamProperties(
                enabled = true,
                streams = streamConfig,
            )
        val initializer = JetStreamStreamInitializer(connection, properties)

        // Call start() to create streams
        initializer.start()

        // Verify streams were created
        val streamInfo1 = jsm.getStreamInfo("test-stream-1")
        assertThat(streamInfo1.config.name).isEqualTo("test-stream-1")
        assertThat(streamInfo1.config.subjects).contains("test.subject.one.*")

        val streamInfo2 = jsm.getStreamInfo("test-stream-2")
        assertThat(streamInfo2.config.name).isEqualTo("test-stream-2")
        assertThat(streamInfo2.config.subjects).contains("test.subject.two.*")
    }

    @Test
    fun `should handle existing streams gracefully`() {
        val connection = createConnection()
        val jsm = connection.jetStreamManagement()

        // Create a stream manually first
        val existingStreamConfig =
            StreamConfiguration
                .builder()
                .name("existing-stream")
                .subjects("existing.subject.*")
                .storageType(StorageType.Memory)
                .build()
        jsm.addStream(existingStreamConfig)

        // Create initializer that includes the same stream name
        val streamConfig =
            mapOf(
                "existing-stream" to
                    JetStreamProperties.StreamProperties(
                        enabled = true,
                        subjects = listOf("existing.subject.*"),
                        storageType = "FILE",
                        retentionPolicy = "LIMITS",
                        discardPolicy = "OLD",
                        maxMessages = 10000,
                    ),
            )
        val properties =
            JetStreamProperties(
                enabled = true,
                streams = streamConfig,
            )
        val initializer = JetStreamStreamInitializer(connection, properties)

        // Should not throw when stream already exists
        initializer.start() // Should complete without exception
        val streamInfo = jsm.getStreamInfo("existing-stream")
        assertThat(streamInfo.config.name).isEqualTo("existing-stream")
    }
}
