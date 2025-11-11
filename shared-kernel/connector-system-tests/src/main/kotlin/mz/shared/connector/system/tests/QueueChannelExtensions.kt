package mz.shared.connector.system.tests

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
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

fun QueueChannel.awaitMessagesAndAssert(
    maxDelayMillis: Long = 6000,
    pollIntervalMillis: Long = 1000,
    assertion: (List<Message<*>>) -> Unit,
) {
    var elapsedTime = 0L

    while (true) {
        val messages = this.clear()
        val result = Result.runCatching { assertion.invoke(messages) }
        if (result.isSuccess) return

        if (elapsedTime >= maxDelayMillis) {
            result.getOrThrow()
            return
        }
        elapsedTime += pollIntervalMillis
        runBlocking {
            delay(pollIntervalMillis)
        }
    }
}
