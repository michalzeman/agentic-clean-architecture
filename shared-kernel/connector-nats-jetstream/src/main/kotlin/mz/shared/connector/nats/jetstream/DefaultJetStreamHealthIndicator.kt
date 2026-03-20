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

    override fun checkHealth(): JetStreamHealthStatus {
        val errors = mutableListOf<String>()
        var connectionState = JetStreamHealthStatus.ConnectionState.DISCONNECTED
        val streamStates = mutableListOf<JetStreamHealthStatus.StreamHealth>()
        val consumerStates = mutableListOf<JetStreamHealthStatus.ConsumerHealth>()

        try {
            val connection = connectionFactory.peekConnection()
            if (connection == null) {
                errors.add("No connection established")
            } else {
                connectionState = mapConnectionStatus(connection.status)

                if (connection.status == Connection.Status.CONNECTED) {
                    val jsm = connection.jetStreamManagement()

                    streamStates.addAll(collectStreamHealth(jsm, errors))
                    consumerStates.addAll(collectConsumerHealth(jsm, errors))
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to check JetStream health", e)
            errors.add("Connection error: ${e.message}")
        }

        val isHealthy =
            connectionState == JetStreamHealthStatus.ConnectionState.CONNECTED &&
                errors.isEmpty() &&
                streamStates.all { it.isHealthy } &&
                consumerStates.all { it.isHealthy }

        return JetStreamHealthStatus(
            isHealthy = isHealthy,
            connectionState = connectionState,
            streamStates = streamStates,
            consumerStates = consumerStates,
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
        errors: MutableList<String>,
    ): List<JetStreamHealthStatus.StreamHealth> {
        val streamHealthList = mutableListOf<JetStreamHealthStatus.StreamHealth>()

        try {
            val streamNames = jsm.streamNames

            for (streamName in streamNames) {
                try {
                    val streamInfo: StreamInfo = jsm.getStreamInfo(streamName)
                    val streamState = streamInfo.streamState

                    streamHealthList.add(
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
                    errors.add("Stream '$streamName': ${e.message}")
                    streamHealthList.add(
                        JetStreamHealthStatus.StreamHealth(
                            streamName = streamName,
                            isHealthy = false,
                            messageCount = -1,
                            byteSize = -1,
                            consumerCount = -1,
                        ),
                    )
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to list streams", e)
            errors.add("Failed to list streams: ${e.message}")
        }

        return streamHealthList
    }

    private fun collectConsumerHealth(
        jsm: JetStreamManagement,
        errors: MutableList<String>,
    ): List<JetStreamHealthStatus.ConsumerHealth> {
        val consumerHealthList = mutableListOf<JetStreamHealthStatus.ConsumerHealth>()

        try {
            val streamNames = jsm.streamNames

            for (streamName in streamNames) {
                try {
                    val consumerNames = jsm.getConsumerNames(streamName)

                    for (consumerName in consumerNames) {
                        try {
                            val consumerInfo: ConsumerInfo = jsm.getConsumerInfo(streamName, consumerName)
                            val delivered = consumerInfo.delivered.consumerSequence
                            val ackFloor = consumerInfo.ackFloor.consumerSequence
                            val pendingCount = delivered - ackFloor

                            consumerHealthList.add(
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
                            errors.add("Consumer '$consumerName' on '$streamName': ${e.message}")
                            consumerHealthList.add(
                                JetStreamHealthStatus.ConsumerHealth(
                                    streamName = streamName,
                                    consumerName = consumerName,
                                    isHealthy = false,
                                    pendingCount = -1,
                                    deliveredCount = -1,
                                    ackPendingCount = -1,
                                ),
                            )
                        }
                    }
                } catch (e: Exception) {
                    logger.warn("Failed to get consumers for stream: $streamName", e)
                    errors.add("Stream '$streamName' consumers: ${e.message}")
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to collect consumer health", e)
            errors.add("Failed to collect consumer health: ${e.message}")
        }

        return consumerHealthList
    }
}
