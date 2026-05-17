package mz.shared.connector.nats.jetstream.starter

import io.nats.client.Connection
import io.nats.client.api.DiscardPolicy
import io.nats.client.api.RetentionPolicy
import io.nats.client.api.StorageType
import io.nats.client.api.StreamConfiguration
import org.slf4j.LoggerFactory
import org.springframework.context.SmartLifecycle
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Initializes JetStream streams at application startup.
 *
 * This class is responsible for creating configured JetStream streams before
 * consumers start subscribing. It implements SmartLifecycle with phase 0 (earliest)
 * to ensure streams exist before consumers attempt to subscribe (which run at default phase).
 *
 * If a stream already exists, it logs a warning but continues without error.
 */
class JetStreamStreamInitializer(
    private val connection: Connection,
    private val properties: JetStreamProperties,
) : SmartLifecycle {
    private val logger = LoggerFactory.getLogger(JetStreamStreamInitializer::class.java)
    private val running = AtomicBoolean(false)

    override fun start() {
        if (running.getAndSet(true)) {
            logger.debug("JetStreamStreamInitializer already started, skipping initialization")
            return
        }

        if (!properties.enabled) {
            logger.debug("JetStream disabled (nats.jetstream.enabled=false), skipping stream initialization")
            return
        }

        val jsm = connection.jetStreamManagement()

        for (streamConfig in properties.streams) {
            if (!streamConfig.enabled) {
                logger.debug("Stream {} is disabled, skipping", streamConfig.name)
                continue
            }

            try {
                val config =
                    StreamConfiguration
                        .builder()
                        .name(streamConfig.name)
                        .apply {
                            // Add subjects
                            if (streamConfig.subjects.isNotEmpty()) {
                                subjects(*streamConfig.subjects.toTypedArray())
                            }
                        }.storageType(toNatsStorageType(streamConfig.storageType))
                        .retentionPolicy(toNatsRetentionPolicy(streamConfig.retentionPolicy))
                        .discardPolicy(toNatsDiscardPolicy(streamConfig.discardPolicy))
                        .apply {
                            // Add optional properties
                            if (streamConfig.maxMessages > 0) {
                                maxMessages(streamConfig.maxMessages)
                            }
                            if (streamConfig.maxBytes > 0) {
                                maxBytes(streamConfig.maxBytes)
                            }
                            if (streamConfig.maxAge != null) {
                                maxAge(streamConfig.maxAge)
                            }
                            if (streamConfig.maxConsumers > 0) {
                                maxConsumers(streamConfig.maxConsumers.toLong())
                            }
                            duplicateWindow(streamConfig.duplicateWindow)
                        }.build()

                jsm.addStream(config)
                logger.info(
                    "Created JetStream stream: name={}, subjects={}, storage={}, retention={}",
                    streamConfig.name,
                    streamConfig.subjects,
                    streamConfig.storageType,
                    streamConfig.retentionPolicy,
                )
            } catch (e: Exception) {
                // Stream name already in use — check message
                if (e.message?.contains("stream name already in use") == true ||
                    e.message?.contains("10058") == true
                ) {
                    logger.warn(
                        "JetStream stream already exists, skipping creation: name={}",
                        streamConfig.name,
                    )
                } else {
                    logger.error(
                        "Failed to create JetStream stream: name={}",
                        streamConfig.name,
                        e,
                    )
                    throw e
                }
            }
        }

        logger.info("JetStreamStreamInitializer completed: {} streams configured", properties.streams.size)
    }

    override fun stop() {
        running.set(false)
        // Streams persist in NATS — no cleanup needed
        logger.debug("JetStreamStreamInitializer stopped")
    }

    override fun isRunning(): Boolean = running.get()

    override fun getPhase(): Int = 0 // Lowest phase: runs before consumers (which use default phase Int.MAX_VALUE)

    private fun toNatsStorageType(storageType: String): StorageType =
        when (storageType.uppercase()) {
            "FILE" -> StorageType.File
            "MEMORY" -> StorageType.Memory
            else -> throw IllegalArgumentException("Unknown storage type: $storageType")
        }

    private fun toNatsRetentionPolicy(policy: String): RetentionPolicy =
        when (policy.uppercase()) {
            "LIMITS" -> RetentionPolicy.Limits
            "INTEREST" -> RetentionPolicy.Interest
            "WORKQUEUE", "WORK_QUEUE" -> RetentionPolicy.WorkQueue
            else -> throw IllegalArgumentException("Unknown retention policy: $policy")
        }

    private fun toNatsDiscardPolicy(policy: String): DiscardPolicy =
        when (policy.uppercase()) {
            "OLD" -> DiscardPolicy.Old
            "NEW" -> DiscardPolicy.New
            else -> throw IllegalArgumentException("Unknown discard policy: $policy")
        }
}
