package mz.shared.connector.nats.jetstream

import io.nats.client.Connection
import io.nats.client.JetStreamManagement
import io.nats.client.api.ConsumerInfo
import io.nats.client.api.StreamInfo
import mz.shared.connector.nats.NatsConnectionFactory
import org.slf4j.LoggerFactory

/**
 * Default implementation of JetStreamHealthIndicator.
 *
 * Monitors JetStream connection health, stream states, and consumer states
 * using the NATS JetStream management API.
 *
 * @param connectionFactory Factory for obtaining NATS connections
 */
class DefaultJetStreamHealthIndicator(
    private val connectionFactory: NatsConnectionFactory,
) : JetStreamHealthIndicator {
    private val logger = LoggerFactory.getLogger(DefaultJetStreamHealthIndicator::class.java)

    override fun checkHealth(): JetStreamHealthStatus =
        try {
            val errors = mutableListOf<String>()
            var connectionState = JetStreamHealthStatus.ConnectionState.DISCONNECTED
            val streamStates = mutableListOf<JetStreamHealthStatus.StreamHealth>()
            val consumerStates = mutableListOf<JetStreamHealthStatus.ConsumerHealth>()

            val connection = connectionFactory.peekConnection()
            if (connection == null) {
                errors.add("No connection established")
            } else {
                connectionState = mapConnectionStatus(connection.status)

                if (connection.status == Connection.Status.CONNECTED) {
                    val jsm = connection.jetStreamManagement()
                    val streamNames = jsm.streamNames

                    val (healths, healthErrors) = collectStreamHealth(jsm, streamNames)
                    streamStates.addAll(healths)
                    errors.addAll(healthErrors)

                    val (collectedHealths, collectedErrors) = collectConsumerHealth(jsm, streamNames)
                    consumerStates.addAll(collectedHealths)
                    errors.addAll(collectedErrors)
                }
            }

            val isHealthy =
                connectionState == JetStreamHealthStatus.ConnectionState.CONNECTED &&
                    errors.isEmpty() &&
                    streamStates.all { it.isHealthy } &&
                    consumerStates.all { it.isHealthy }
            JetStreamHealthStatus(
                isHealthy = isHealthy,
                connectionState = connectionState,
                streamStates = streamStates,
                consumerStates = consumerStates,
                errors = errors,
            )
        } catch (e: Exception) {
            logger.error("Failed to check JetStream health", e)
            val errors = listOf("Connection error: ${e.message}")

            JetStreamHealthStatus(
                isHealthy = false,
                connectionState = JetStreamHealthStatus.ConnectionState.DISCONNECTED,
                errors = errors,
            )
        }

    private fun mapConnectionStatus(status: Connection.Status): JetStreamHealthStatus.ConnectionState =
        when (status) {
            Connection.Status.CONNECTED -> JetStreamHealthStatus.ConnectionState.CONNECTED
            Connection.Status.RECONNECTING -> JetStreamHealthStatus.ConnectionState.RECONNECTING
            Connection.Status.CLOSED -> JetStreamHealthStatus.ConnectionState.DISCONNECTED
            else -> JetStreamHealthStatus.ConnectionState.FAILED
        }

    private fun collectStreamHealth(
        jsm: JetStreamManagement,
        streamNames: List<String>,
    ): HealthResult<List<JetStreamHealthStatus.StreamHealth>> =
        streamNames
            .map { streamName ->
                try {
                    val streamInfo: StreamInfo = jsm.getStreamInfo(streamName)
                    val streamState = streamInfo.streamState

                    HealthResult(
                        JetStreamHealthStatus.StreamHealth(
                            streamName = streamName,
                            isHealthy = streamState.msgCount >= 0,
                            messageCount = streamState.msgCount,
                            byteSize = streamState.byteCount,
                            consumerCount = streamState.consumerCount.toInt(),
                        ),
                    )
                } catch (e: Exception) {
                    logger.warn("Failed to get stream info for: $streamName", e)
                    HealthResult(
                        JetStreamHealthStatus.StreamHealth(
                            streamName = streamName,
                            isHealthy = false,
                            messageCount = -1,
                            byteSize = -1,
                            consumerCount = -1,
                        ),
                        listOf("Stream '$streamName': ${e.message}"),
                    )
                }
            }.fold(HealthResult(emptyList())) { acc, healResult ->
                HealthResult(
                    result = acc.result + healResult.result,
                    errors = acc.errors + healResult.errors,
                )
            }

    private fun collectConsumerHealth(
        jsm: JetStreamManagement,
        streamNames: List<String>,
    ): HealthResult<List<JetStreamHealthStatus.ConsumerHealth>> =
        try {
            streamNames
                .map { streamName ->
                    collectHealthByConsumerNames(jsm, streamName)
                }.fold(HealthResult(emptyList())) { acc, healthResult ->
                    HealthResult(
                        result = acc.result + healthResult.result,
                        errors = acc.errors + healthResult.errors,
                    )
                }
        } catch (e: Exception) {
            logger.error("Failed to collect consumer health", e)
            val error = "Failed to collect consumer health: ${e.message}"
            HealthResult(emptyList(), listOf(error))
        }

    private fun collectHealthByConsumerNames(
        jsm: JetStreamManagement,
        streamName: String,
    ): HealthResult<List<JetStreamHealthStatus.ConsumerHealth>> =
        try {
            val consumerNames = jsm.getConsumerNames(streamName)
            val healthResults =
                consumerNames.map { consumerName ->
                    collectHealthByConsumerName(streamName, jsm, consumerName)
                }
            healthResults.fold(HealthResult(emptyList())) { acc, healthResult ->
                HealthResult(
                    result = acc.result + healthResult.result,
                    errors = acc.errors + healthResult.errors,
                )
            }
        } catch (e: Exception) {
            logger.warn("Failed to get consumer names for stream: $streamName", e)
            val error = "Stream '$streamName' consumers: ${e.message}"
            HealthResult(emptyList(), listOf(error))
        }

    private fun collectHealthByConsumerName(
        streamName: String,
        jsm: JetStreamManagement,
        consumerName: String,
    ): HealthResult<JetStreamHealthStatus.ConsumerHealth> =
        try {
            val consumerInfo: ConsumerInfo = jsm.getConsumerInfo(streamName, consumerName)
            val delivered = consumerInfo.delivered.consumerSequence
            val ackFloor = consumerInfo.ackFloor.consumerSequence
            val pendingCount = delivered - ackFloor

            HealthResult(
                JetStreamHealthStatus.ConsumerHealth(
                    streamName = streamName,
                    consumerName = consumerName,
                    isHealthy = pendingCount >= 0,
                    pendingCount = pendingCount.coerceAtLeast(0),
                    deliveredCount = delivered,
                    ackPendingCount = consumerInfo.numAckPending,
                ),
            )
        } catch (e: Exception) {
            logger.warn(
                "Failed to get consumer info for: $consumerName on stream: $streamName",
                e,
            )
            val error = "Consumer '$consumerName' on '$streamName': ${e.message}"
            HealthResult(
                JetStreamHealthStatus.ConsumerHealth(
                    streamName = streamName,
                    consumerName = consumerName,
                    isHealthy = false,
                    pendingCount = -1,
                    deliveredCount = -1,
                    ackPendingCount = -1,
                ),
                listOf(error),
            )
        }
}

private data class HealthResult<T>(
    val result: T,
    val errors: List<String> = emptyList(),
)
