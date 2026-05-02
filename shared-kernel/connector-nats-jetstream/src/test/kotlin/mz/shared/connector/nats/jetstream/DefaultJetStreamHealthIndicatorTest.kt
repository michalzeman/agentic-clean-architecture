package mz.shared.connector.nats.jetstream

import io.nats.client.Connection
import io.nats.client.JetStreamManagement
import io.nats.client.api.ConsumerInfo
import io.nats.client.api.SequenceInfo
import io.nats.client.api.StreamInfo
import io.nats.client.api.StreamState
import mz.shared.connector.nats.NatsConnectionFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DefaultJetStreamHealthIndicatorTest {
    @Mock
    private lateinit var connectionFactory: NatsConnectionFactory

    private val healthIndicator by lazy { DefaultJetStreamHealthIndicator(connectionFactory) }

    private fun createMockConnection(status: Connection.Status): Connection =
        mock {
            whenever(it.status).thenReturn(status)
        }

    private fun createMockStreamState(
        msgCount: Long = 100L,
        byteCount: Long = 1024L,
        consumerCount: Long = 2L,
    ): StreamState =
        mock {
            whenever(it.msgCount).thenReturn(msgCount)
            whenever(it.byteCount).thenReturn(byteCount)
            whenever(it.consumerCount).thenReturn(consumerCount)
        }

    private fun createMockStreamInfo(streamState: StreamState): StreamInfo =
        mock {
            whenever(it.streamState).thenReturn(streamState)
        }

    private fun createMockConsumerInfo(
        deliveredSequence: Long = 50L,
        ackFloorSequence: Long = 45L,
        numAckPending: Long = 5L,
    ): ConsumerInfo {
        val delivered =
            mock<SequenceInfo> {
                whenever(it.consumerSequence).thenReturn(deliveredSequence)
            }
        val ackFloor =
            mock<SequenceInfo> {
                whenever(it.consumerSequence).thenReturn(ackFloorSequence)
            }
        return mock {
            whenever(it.delivered).thenReturn(delivered)
            whenever(it.ackFloor).thenReturn(ackFloor)
            whenever(it.numAckPending).thenReturn(numAckPending)
        }
    }

    // =====================================================
    // Connection State Tests
    // =====================================================

    @Test
    fun `should return healthy status when connection is connected and no streams`() {
        // Given
        val connection = createMockConnection(Connection.Status.CONNECTED)
        whenever(connectionFactory.peekConnection()).thenReturn(connection)

        val jsm = mock<JetStreamManagement>()
        whenever(jsm.streamNames).thenReturn(emptyList())
        whenever(connection.jetStreamManagement()).thenReturn(jsm)

        // When
        val result = healthIndicator.checkHealth()

        // Then
        assertThat(result.isHealthy).isTrue()
        assertThat(result.connectionState).isEqualTo(JetStreamHealthStatus.ConnectionState.CONNECTED)
        assertThat(result.streamStates).isEmpty()
        assertThat(result.consumerStates).isEmpty()
        assertThat(result.errors).isEmpty()
    }

    @Test
    fun `should return disconnected status when connection is null`() {
        // Given
        whenever(connectionFactory.peekConnection()).thenReturn(null)

        // When
        val result = healthIndicator.checkHealth()

        // Then
        assertThat(result.isHealthy).isFalse()
        assertThat(result.connectionState).isEqualTo(JetStreamHealthStatus.ConnectionState.DISCONNECTED)
        assertThat(result.errors).hasSize(1)
        assertThat(result.errors.first()).contains("No connection established")
    }

    @Test
    fun `should return disconnected status when connection status is CLOSED`() {
        // Given
        val connection = createMockConnection(Connection.Status.CLOSED)
        whenever(connectionFactory.peekConnection()).thenReturn(connection)

        // When
        val result = healthIndicator.checkHealth()

        // Then
        assertThat(result.isHealthy).isFalse()
        assertThat(result.connectionState).isEqualTo(JetStreamHealthStatus.ConnectionState.DISCONNECTED)
        assertThat(result.errors).isEmpty()
    }

    @Test
    fun `should return reconnecting status when connection status is RECONNECTING`() {
        // Given
        val connection = createMockConnection(Connection.Status.RECONNECTING)
        whenever(connectionFactory.peekConnection()).thenReturn(connection)

        // When
        val result = healthIndicator.checkHealth()

        // Then
        assertThat(result.isHealthy).isFalse()
        assertThat(result.connectionState).isEqualTo(JetStreamHealthStatus.ConnectionState.RECONNECTING)
    }

    @Test
    fun `should return failed status when connection status is unknown`() {
        // Given
        val connection = createMockConnection(Connection.Status.DISCONNECTED)
        whenever(connectionFactory.peekConnection()).thenReturn(connection)

        // When
        val result = healthIndicator.checkHealth()

        // Then
        assertThat(result.isHealthy).isFalse()
        assertThat(result.connectionState).isEqualTo(JetStreamHealthStatus.ConnectionState.FAILED)
    }

    @Test
    fun `should return unhealthy when connection factory throws exception`() {
        // Given
        val exception = RuntimeException("Connection factory failed")
        whenever(connectionFactory.peekConnection()).thenThrow(exception)

        // When
        val result = healthIndicator.checkHealth()

        // Then
        assertThat(result.isHealthy).isFalse()
        assertThat(result.connectionState).isEqualTo(JetStreamHealthStatus.ConnectionState.DISCONNECTED)
        assertThat(result.errors).hasSize(1)
        assertThat(result.errors.first()).contains("Connection factory failed")
    }

    // =====================================================
    // Stream Health Tests
    // =====================================================

    @Test
    fun `should return healthy stream status when streams exist and are accessible`() {
        // Given
        val connection = createMockConnection(Connection.Status.CONNECTED)
        whenever(connectionFactory.peekConnection()).thenReturn(connection)

        val streamState1 = createMockStreamState(100L, 1024L, 5L)
        val streamInfo1 = createMockStreamInfo(streamState1)
        val streamState2 = createMockStreamState(200L, 2048L, 3L)
        val streamInfo2 = createMockStreamInfo(streamState2)

        val jsm = mock<JetStreamManagement>()
        whenever(jsm.streamNames).thenReturn(listOf("stream1", "stream2"))
        whenever(jsm.getStreamInfo("stream1")).thenReturn(streamInfo1)
        whenever(jsm.getStreamInfo("stream2")).thenReturn(streamInfo2)
        whenever(jsm.getConsumerNames("stream1")).thenReturn(emptyList())
        whenever(jsm.getConsumerNames("stream2")).thenReturn(emptyList())
        whenever(connection.jetStreamManagement()).thenReturn(jsm)

        // When
        val result = healthIndicator.checkHealth()

        // Then
        assertThat(result.isHealthy).isTrue()
        assertThat(result.connectionState).isEqualTo(JetStreamHealthStatus.ConnectionState.CONNECTED)
        assertThat(result.streamStates).hasSize(2)
        val stream1 = result.streamStates.single { it.streamName == "stream1" }
        assertThat(stream1.messageCount).isEqualTo(100L)
        assertThat(stream1.byteSize).isEqualTo(1024L)
        assertThat(stream1.consumerCount).isEqualTo(5)
        assertThat(result.streamStates).anyMatch { it.streamName == "stream2" }
    }

    @Test
    fun `should return stream error when stream info cannot be retrieved`() {
        // Given
        val connection = createMockConnection(Connection.Status.CONNECTED)
        whenever(connectionFactory.peekConnection()).thenReturn(connection)

        val healthyStreamState = createMockStreamState(50L, 512L, 2L)
        val healthyStreamInfo = createMockStreamInfo(healthyStreamState)

        val jsm = mock<JetStreamManagement>()
        val errorMessage = "Stream not found"
        whenever(jsm.streamNames).thenReturn(listOf("healthy-stream", "missing-stream"))
        whenever(jsm.getStreamInfo("healthy-stream")).thenReturn(healthyStreamInfo)
        whenever(jsm.getStreamInfo("missing-stream")).thenThrow(RuntimeException(errorMessage))
        whenever(connection.jetStreamManagement()).thenReturn(jsm)

        // When
        val result = healthIndicator.checkHealth()

        // Then
        assertThat(result.isHealthy).isFalse
        assertThat(result.streamStates).hasSize(2)
        assertThat(result.streamStates).anyMatch { it.streamName == "healthy-stream" && it.isHealthy }
        assertThat(result.errors).hasSize(1)
        assertThat(result.errors.first()).contains("missing-stream")
        assertThat(result.errors.first()).contains(errorMessage)
    }

    @Test
    fun `should return empty streams list when no streams exist`() {
        // Given
        val connection = createMockConnection(Connection.Status.CONNECTED)
        whenever(connectionFactory.peekConnection()).thenReturn(connection)

        val jsm = mock<JetStreamManagement>()
        whenever(jsm.streamNames).thenReturn(emptyList())
        whenever(connection.jetStreamManagement()).thenReturn(jsm)

        // When
        val result = healthIndicator.checkHealth()

        // Then
        assertThat(result.isHealthy).isTrue()
        assertThat(result.streamStates).isEmpty()
        assertThat(result.errors).isEmpty()
    }

    @Test
    fun `should return error when listing streams fails`() {
        // Given
        val connection = createMockConnection(Connection.Status.CONNECTED)
        whenever(connectionFactory.peekConnection()).thenReturn(connection)

        val errorMessage = "Failed to list streams"
        val jsm = mock<JetStreamManagement>()
        whenever(jsm.streamNames).thenThrow(RuntimeException(errorMessage))
        whenever(connection.jetStreamManagement()).thenReturn(jsm)

        // When
        val result = healthIndicator.checkHealth()

        // Then
        assertThat(result.isHealthy).isFalse()
        assertThat(result.streamStates).isEmpty()
        assertThat(result.errors).hasSize(1)
        assertThat(result.errors.first()).contains("Failed to list streams")
        assertThat(result.errors.first()).contains(errorMessage)
    }

    // =====================================================
    // Consumer Health Tests
    // =====================================================

    @Test
    fun `should return healthy consumer status when consumers exist`() {
        // Given
        val connection = createMockConnection(Connection.Status.CONNECTED)
        whenever(connectionFactory.peekConnection()).thenReturn(connection)

        val streamState = createMockStreamState(100L, 1024L, 2L)
        val streamInfo = createMockStreamInfo(streamState)
        val consumerInfo = createMockConsumerInfo(50L, 45L, 5L)

        val jsm = mock<JetStreamManagement>()
        whenever(jsm.streamNames).thenReturn(listOf("stream1"))
        whenever(jsm.getStreamInfo("stream1")).thenReturn(streamInfo)
        whenever(jsm.getConsumerNames("stream1")).thenReturn(listOf("consumer1"))
        whenever(jsm.getConsumerInfo("stream1", "consumer1")).thenReturn(consumerInfo)
        whenever(connection.jetStreamManagement()).thenReturn(jsm)

        // When
        val result = healthIndicator.checkHealth()

        // Then
        assertThat(result.isHealthy).isTrue()
        assertThat(result.consumerStates).hasSize(1)
        assertThat(result.consumerStates.first().streamName).isEqualTo("stream1")
        assertThat(result.consumerStates.first().consumerName).isEqualTo("consumer1")
        assertThat(result.consumerStates.first().pendingCount).isEqualTo(5L)
        assertThat(result.consumerStates.first().deliveredCount).isEqualTo(50L)
        assertThat(result.consumerStates.first().ackPendingCount).isEqualTo(5L)
    }

    @Test
    fun `should return consumer error when consumer info cannot be retrieved`() {
        // Given
        val connection = createMockConnection(Connection.Status.CONNECTED)
        whenever(connectionFactory.peekConnection()).thenReturn(connection)

        val streamState = createMockStreamState(100L, 1024L, 2L)
        val streamInfo = createMockStreamInfo(streamState)
        val errorMessage = "Consumer not found"

        val healthyConsumerInfo = createMockConsumerInfo()

        val jsm = mock<JetStreamManagement>()
        whenever(jsm.streamNames).thenReturn(listOf("stream1"))
        whenever(jsm.getStreamInfo("stream1")).thenReturn(streamInfo)
        whenever(jsm.getConsumerNames("stream1")).thenReturn(listOf("healthy-consumer", "missing-consumer"))
        whenever(jsm.getConsumerInfo("stream1", "healthy-consumer")).thenReturn(healthyConsumerInfo)
        whenever(jsm.getConsumerInfo("stream1", "missing-consumer")).thenThrow(RuntimeException(errorMessage))
        whenever(connection.jetStreamManagement()).thenReturn(jsm)

        // When
        val result = healthIndicator.checkHealth()

        // Then
        assertThat(result.isHealthy).isFalse() // Unhealthy: any consumer error is treated as a health failure
        assertThat(result.consumerStates).hasSize(2)
        assertThat(result.errors).hasSize(1)
        assertThat(result.errors.first()).contains("missing-consumer")
        assertThat(result.errors.first()).contains(errorMessage)
    }

    @Test
    fun `should return empty consumers list when no consumers exist`() {
        // Given
        val connection = createMockConnection(Connection.Status.CONNECTED)
        whenever(connectionFactory.peekConnection()).thenReturn(connection)

        val streamState = createMockStreamState(100L, 1024L, 0L)
        val streamInfo = createMockStreamInfo(streamState)

        val jsm = mock<JetStreamManagement>()
        whenever(jsm.streamNames).thenReturn(listOf("stream1"))
        whenever(jsm.getStreamInfo("stream1")).thenReturn(streamInfo)
        whenever(jsm.getConsumerNames("stream1")).thenReturn(emptyList())
        whenever(connection.jetStreamManagement()).thenReturn(jsm)

        // When
        val result = healthIndicator.checkHealth()

        // Then
        assertThat(result.isHealthy).isTrue()
        assertThat(result.consumerStates).isEmpty()
        assertThat(result.errors).isEmpty()
    }

    @Test
    fun `should handle error when listing consumers for a stream fails`() {
        // Given
        val connection = createMockConnection(Connection.Status.CONNECTED)
        whenever(connectionFactory.peekConnection()).thenReturn(connection)

        val streamState = createMockStreamState(100L, 1024L, 1L)
        val streamInfo = createMockStreamInfo(streamState)

        val errorMessage = "Failed to list consumers"
        val jsm = mock<JetStreamManagement>()
        whenever(jsm.streamNames).thenReturn(listOf("stream1"))
        whenever(jsm.getStreamInfo("stream1")).thenReturn(streamInfo)
        whenever(jsm.getConsumerNames("stream1")).thenThrow(RuntimeException(errorMessage))
        whenever(connection.jetStreamManagement()).thenReturn(jsm)

        // When
        val result = healthIndicator.checkHealth()

        // Then
        assertThat(result.isHealthy).isFalse()
        assertThat(result.errors).hasSize(1)
        assertThat(result.errors.first()).contains(errorMessage)
    }

    // =====================================================
    // Combined Health Tests
    // =====================================================

    @Test
    fun `should return unhealthy when some streams are unhealthy`() {
        // Given
        val connection = createMockConnection(Connection.Status.CONNECTED)
        whenever(connectionFactory.peekConnection()).thenReturn(connection)

        val healthyStreamState = createMockStreamState(100L, 1024L, 2L)
        val healthyStreamInfo = createMockStreamInfo(healthyStreamState)

        val unhealthyStreamState = createMockStreamState(-1L, 0L, 0L) // Negative message count indicates unhealthy
        val unhealthyStreamInfo = createMockStreamInfo(unhealthyStreamState)

        val jsm = mock<JetStreamManagement>()
        whenever(jsm.streamNames).thenReturn(listOf("healthy-stream", "unhealthy-stream"))
        whenever(jsm.getStreamInfo("healthy-stream")).thenReturn(healthyStreamInfo)
        whenever(jsm.getStreamInfo("unhealthy-stream")).thenReturn(unhealthyStreamInfo)
        whenever(jsm.getConsumerNames("healthy-stream")).thenReturn(emptyList())
        whenever(jsm.getConsumerNames("unhealthy-stream")).thenReturn(emptyList())
        whenever(connection.jetStreamManagement()).thenReturn(jsm)

        // When
        val result = healthIndicator.checkHealth()

        // Then
        assertThat(result.isHealthy).isFalse()
        assertThat(result.streamStates).hasSize(2)
        assertThat(result.streamStates.single { it.streamName == "healthy-stream" }.isHealthy).isTrue()
        assertThat(result.streamStates.single { it.streamName == "unhealthy-stream" }.isHealthy).isFalse()
    }

    @Test
    fun `should return unhealthy when some consumers are unhealthy`() {
        // Given
        val connection = createMockConnection(Connection.Status.CONNECTED)
        whenever(connectionFactory.peekConnection()).thenReturn(connection)

        val streamState = createMockStreamState(100L, 1024L, 2L)
        val streamInfo = createMockStreamInfo(streamState)

        // Unhealthy consumer: delivered < ackFloor (negative pending)
        val unhealthyConsumerInfo = createMockConsumerInfo(40L, 50L, 10L)
        // Healthy consumer
        val healthyConsumerInfo = createMockConsumerInfo(50L, 45L, 5L)

        val jsm = mock<JetStreamManagement>()
        whenever(jsm.streamNames).thenReturn(listOf("stream1"))
        whenever(jsm.getStreamInfo("stream1")).thenReturn(streamInfo)
        whenever(jsm.getConsumerNames("stream1")).thenReturn(listOf("healthy-consumer", "unhealthy-consumer"))
        whenever(jsm.getConsumerInfo("stream1", "healthy-consumer")).thenReturn(healthyConsumerInfo)
        whenever(jsm.getConsumerInfo("stream1", "unhealthy-consumer")).thenReturn(unhealthyConsumerInfo)
        whenever(connection.jetStreamManagement()).thenReturn(jsm)

        // When
        val result = healthIndicator.checkHealth()

        // Then
        assertThat(result.isHealthy).isFalse()
        assertThat(result.consumerStates).hasSize(2)
        assertThat(result.consumerStates.single { it.consumerName == "healthy-consumer" }.isHealthy).isTrue()
        assertThat(result.consumerStates.single { it.consumerName == "unhealthy-consumer" }.isHealthy).isFalse()
    }

    @Test
    fun `should aggregate errors from both streams and consumers`() {
        // Given
        val connection = createMockConnection(Connection.Status.CONNECTED)
        whenever(connectionFactory.peekConnection()).thenReturn(connection)

        val streamState = createMockStreamState(100L, 1024L, 2L)
        val streamInfo = createMockStreamInfo(streamState)

        val jsm = mock<JetStreamManagement>()
        whenever(jsm.streamNames).thenReturn(listOf("stream1", "stream2"))
        whenever(jsm.getStreamInfo("stream1")).thenThrow(RuntimeException("Stream 1 error"))
        whenever(jsm.getStreamInfo("stream2")).thenReturn(streamInfo)
        whenever(jsm.getConsumerNames("stream1")).thenThrow(RuntimeException("Consumer list error"))
        whenever(jsm.getConsumerNames("stream2")).thenReturn(listOf("consumer1"))
        whenever(jsm.getConsumerInfo("stream2", "consumer1")).thenThrow(RuntimeException("Consumer error"))
        whenever(connection.jetStreamManagement()).thenReturn(jsm)

        // When
        val result = healthIndicator.checkHealth()

        // Then
        assertThat(result.isHealthy).isFalse()
        assertThat(result.errors).hasSize(3)
        assertThat(result.errors).anyMatch { it.contains("Stream 1 error") }
        assertThat(result.errors).anyMatch { it.contains("Consumer list error") }
        assertThat(result.errors).anyMatch { it.contains("Consumer error") }
    }

    // =====================================================
    // Edge Cases
    // =====================================================

    @Test
    fun `should handle stream with zero messages`() {
        // Given
        val connection = createMockConnection(Connection.Status.CONNECTED)
        whenever(connectionFactory.peekConnection()).thenReturn(connection)

        val streamState = createMockStreamState(0L, 0L, 0L)
        val streamInfo = createMockStreamInfo(streamState)

        val jsm = mock<JetStreamManagement>()
        whenever(jsm.streamNames).thenReturn(listOf("empty-stream"))
        whenever(jsm.getStreamInfo("empty-stream")).thenReturn(streamInfo)
        whenever(jsm.getConsumerNames("empty-stream")).thenReturn(emptyList())
        whenever(connection.jetStreamManagement()).thenReturn(jsm)

        // When
        val result = healthIndicator.checkHealth()

        // Then
        assertThat(result.isHealthy).isTrue()
        assertThat(result.streamStates).hasSize(1)
        assertThat(result.streamStates.first().messageCount).isEqualTo(0L)
        assertThat(result.streamStates.first().byteSize).isEqualTo(0L)
    }

    @Test
    fun `should handle consumer with zero pending messages`() {
        // Given
        val connection = createMockConnection(Connection.Status.CONNECTED)
        whenever(connectionFactory.peekConnection()).thenReturn(connection)

        val streamState = createMockStreamState(100L, 1024L, 1L)
        val streamInfo = createMockStreamInfo(streamState)
        val consumerInfo = createMockConsumerInfo(100L, 100L, 0L)

        val jsm = mock<JetStreamManagement>()
        whenever(jsm.streamNames).thenReturn(listOf("stream1"))
        whenever(jsm.getStreamInfo("stream1")).thenReturn(streamInfo)
        whenever(jsm.getConsumerNames("stream1")).thenReturn(listOf("consumer1"))
        whenever(jsm.getConsumerInfo("stream1", "consumer1")).thenReturn(consumerInfo)
        whenever(connection.jetStreamManagement()).thenReturn(jsm)

        // When
        val result = healthIndicator.checkHealth()

        // Then
        assertThat(result.isHealthy).isTrue()
        assertThat(result.consumerStates).hasSize(1)
        assertThat(result.consumerStates.first().pendingCount).isEqualTo(0L)
        assertThat(result.consumerStates.first().ackPendingCount).isEqualTo(0L)
    }

    @Test
    fun `should handle multiple streams with multiple consumers`() {
        // Given
        val connection = createMockConnection(Connection.Status.CONNECTED)
        whenever(connectionFactory.peekConnection()).thenReturn(connection)

        val streamState1 = createMockStreamState(100L, 1024L, 2L)
        val streamInfo1 = createMockStreamInfo(streamState1)
        val streamState2 = createMockStreamState(200L, 2048L, 1L)
        val streamInfo2 = createMockStreamInfo(streamState2)

        val consumerInfo1 = createMockConsumerInfo(50L, 45L, 5L)
        val consumerInfo2 = createMockConsumerInfo(80L, 80L, 0L)

        val jsm = mock<JetStreamManagement>()
        whenever(jsm.streamNames).thenReturn(listOf("stream1", "stream2"))
        whenever(jsm.getStreamInfo("stream1")).thenReturn(streamInfo1)
        whenever(jsm.getStreamInfo("stream2")).thenReturn(streamInfo2)
        whenever(jsm.getConsumerNames("stream1")).thenReturn(listOf("consumer1", "consumer2"))
        whenever(jsm.getConsumerNames("stream2")).thenReturn(listOf("consumer3"))
        whenever(jsm.getConsumerInfo("stream1", "consumer1")).thenReturn(consumerInfo1)
        whenever(jsm.getConsumerInfo("stream1", "consumer2")).thenReturn(consumerInfo2)
        whenever(jsm.getConsumerInfo("stream2", "consumer3")).thenReturn(consumerInfo2)
        whenever(connection.jetStreamManagement()).thenReturn(jsm)

        // When
        val result = healthIndicator.checkHealth()

        // Then
        assertThat(result.isHealthy).isTrue()
        assertThat(result.streamStates).hasSize(2)
        assertThat(result.consumerStates).hasSize(3)
        assertThat(result.errors).isEmpty()
    }
}
