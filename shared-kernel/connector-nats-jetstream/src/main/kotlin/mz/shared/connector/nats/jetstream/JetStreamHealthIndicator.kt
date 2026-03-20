package mz.shared.connector.nats.jetstream

/**
 * Health status for JetStream components.
 */
data class JetStreamHealthStatus(
    val isHealthy: Boolean,
    val connectionState: ConnectionState,
    val streamStates: List<StreamHealth> = emptyList(),
    val consumerStates: List<ConsumerHealth> = emptyList(),
    val errors: List<String> = emptyList(),
) {
    enum class ConnectionState {
        CONNECTED,
        DISCONNECTED,
        RECONNECTING,
        FAILED,
    }

    data class StreamHealth(
        val streamName: String,
        val isHealthy: Boolean,
        val messageCount: Long,
        val byteSize: Long,
        val consumerCount: Int,
    )

    data class ConsumerHealth(
        val streamName: String,
        val consumerName: String,
        val isHealthy: Boolean,
        val pendingCount: Long,
        val deliveredCount: Long,
        val ackPendingCount: Long,
    )
}

/**
 * Interface for monitoring JetStream health.
 */
interface JetStreamHealthIndicator {
    /**
     * Performs health check on JetStream connection and resources.
     */
    fun checkHealth(): JetStreamHealthStatus
}

/**
 * Metrics for JetStream operations.
 */
data class JetStreamMetrics(
    val publishedMessages: Long,
    val consumedMessages: Long,
    val acknowledgedMessages: Long,
    val redeliveredMessages: Long,
    val failedMessages: Long,
    val averagePublishLatencyMillis: Double,
    val averageConsumeLatencyMillis: Double,
)

/**
 * Interface for collecting JetStream metrics.
 */
interface JetStreamMetricsCollector {
    /**
     * Records a publish operation.
     */
    fun recordPublish(
        durationMillis: Long,
        success: Boolean,
    )

    /**
     * Records a consume operation.
     */
    fun recordConsume(
        durationMillis: Long,
        success: Boolean,
    )

    /**
     * Records an acknowledge operation.
     */
    fun recordAcknowledge(success: Boolean)

    /**
     * Gets current metrics snapshot.
     */
    fun getMetrics(): JetStreamMetrics
}
