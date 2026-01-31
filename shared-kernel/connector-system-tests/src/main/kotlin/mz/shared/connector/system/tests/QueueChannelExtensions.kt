package mz.shared.connector.system.tests

import kotlinx.coroutines.delay
import org.springframework.integration.channel.QueueChannel
import org.springframework.messaging.Message

/**
 * Waits until the QueueChannel has messages or the maximum delay is reached.
 * Polls the channel at regular intervals to minimize wait time.
 *
 * @param maxDelayMillis Maximum time to wait in milliseconds (default: 6000)
 * @param pollIntervalMillis Interval between checks in milliseconds (default: 100)
 * @return List of messages from the channel
 */
suspend fun QueueChannel.awaitMessages(
    maxDelayMillis: Long = 6000,
    pollIntervalMillis: Long = 100,
): List<Message<*>> {
    val startTime = System.currentTimeMillis()

    while (true) {
        val messages = this.clear()
        if (messages.isNotEmpty()) {
            return messages
        }

        val elapsedTime = System.currentTimeMillis() - startTime
        if (elapsedTime >= maxDelayMillis) {
            return emptyList()
        }

        delay(pollIntervalMillis)
    }
}

/**
 * Waits until the QueueChannel has messages or the maximum delay is reached.
 * Polls the channel at regular intervals to minimize wait time.
 *
 * @param maxDelayMillis Maximum time to wait in milliseconds (default: 6000)
 * @param pollIntervalMillis Interval between checks in milliseconds (default: 100)
 * @param numberOfMessages Number of messages to wait for
 * @return List of messages from the channel
 */
suspend fun QueueChannel.awaitMessagesCount(
    maxDelayMillis: Long = 6000,
    pollIntervalMillis: Long = 100,
    numberOfMessages: Int,
): List<Message<*>> {
    val totalMessages = mutableListOf<Message<*>>()
    val startTime = System.currentTimeMillis()

    while (true) {
        val messages = this.clear()
        if (messages.isNotEmpty()) {
            totalMessages.addAll(messages)
        }

        if (totalMessages.size >= numberOfMessages) return totalMessages

        val elapsedTime = System.currentTimeMillis() - startTime
        if (elapsedTime >= maxDelayMillis) {
            return totalMessages
        }

        delay(pollIntervalMillis)
    }
}

/**
 * Waits until the QueueChannel has messages or the maximum delay is reached.
 * Polls the channel at regular intervals to minimize wait time.
 *
 * @param maxDelayMillis Maximum time to wait in milliseconds (default: 6000)
 * @param pollIntervalMillis Interval between checks in milliseconds (default: 100)
 * @param assertion Function to determine if the desired condition is met
 * @return List of messages from the channel
 */
suspend fun QueueChannel.awaitMessages(
    maxDelayMillis: Long = 6000,
    pollIntervalMillis: Long = 100,
    assertion: (List<Message<*>>) -> Boolean,
): List<Message<*>> {
    val totalMessages = mutableListOf<Message<*>>()
    val startTime = System.currentTimeMillis()

    while (true) {
        val messages = this.clear()
        if (messages.isNotEmpty()) {
            totalMessages.addAll(messages)
        }

        if (assertion(totalMessages)) return totalMessages

        val elapsedTime = System.currentTimeMillis() - startTime
        if (elapsedTime >= maxDelayMillis) {
            return totalMessages
        }

        delay(pollIntervalMillis)
    }
}

// assertion: (List<Message<*>>) -> Unit,
