package mz.bank.transaction.adapter.redis.stream

import mz.shared.connector.redis.RedisStreamPublisher
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.integration.annotation.Poller
import org.springframework.integration.annotation.ServiceActivator
import org.springframework.messaging.Message
import org.springframework.stereotype.Component
import mz.bank.transaction.contract.proto.BankTransactionEvent as ProtoBankTransactionEvent

private val logger = LoggerFactory.getLogger(RedisStreamBankTransactionEventsPublisher::class.java)

/**
 * Service activator that publishes bank transaction protobuf events to Redis stream.
 * Listens to the outboundBankTransactionRedisStreamChannel and publishes protobuf events
 * to the configured Redis stream.
 */
@Component
class RedisStreamBankTransactionEventsPublisher(
    private val bankTransactionEventsRedisStreamPublisher: RedisStreamPublisher<ProtoBankTransactionEvent>,
    @param:Value("\${adapters.redis.stream.poller-delay-ms:100}") private val pollerDelayMs: Long,
) {
    @ServiceActivator(
        inputChannel = "outboundBankTransactionRedisStreamChannel",
        requiresReply = "false",
        poller =
            Poller(
                fixedDelay = "\${app.integration.redis.poller.fixed-delay:100}",
                maxMessagesPerPoll = "\${app.integration.redis.poller.max-messages-per-poll:10}",
                taskExecutor = "redisStreamTaskExecutor",
            ),
        async = "true",
    )
    suspend fun publish(message: Message<ProtoBankTransactionEvent>) {
        val event = message.payload
        logger.info("Publishing bank transaction protobuf event to Redis stream $event")

        bankTransactionEventsRedisStreamPublisher.publish(message)
    }
}
