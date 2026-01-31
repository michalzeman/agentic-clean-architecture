package mz.bank.account.adapter.redis.stream

import mz.shared.connector.redis.RedisStreamPublisher
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.integration.annotation.Poller
import org.springframework.integration.annotation.ServiceActivator
import org.springframework.messaging.Message
import org.springframework.stereotype.Component
import mz.bank.account.contract.proto.BankAccountEvent as ProtoBankAccountEvent

private val logger = LoggerFactory.getLogger(RedisStreamBankAccountEventsPublisher::class.java)

/**
 * Service activator that publishes bank account protobuf events to Redis stream.
 * Listens to the outboundBankAccountRedisStreamChannel and publishes protobuf events
 * to the configured Redis stream.
 */
@Component
class RedisStreamBankAccountEventsPublisher(
    private val bankAccountEventsRedisStreamPublisher: RedisStreamPublisher<ProtoBankAccountEvent>,
    @param:Value("\${adapters.redis.stream.poller-delay-ms:100}") private val pollerDelayMs: Long,
) {
    @ServiceActivator(
        inputChannel = "outboundBankAccountRedisStreamChannel",
        requiresReply = "false",
        poller =
            Poller(
                fixedDelay = "\${app.integration.redis.poller.fixed-delay:100}",
                maxMessagesPerPoll = "\${app.integration.redis.poller.max-messages-per-poll:10}",
                taskExecutor = "redisStreamTaskExecutor",
            ),
        async = "true",
    )
    suspend fun publish(message: Message<ProtoBankAccountEvent>) {
        val event = message.payload
        logger.info("Publishing bank account protobuf event to Redis stream $event")

        bankAccountEventsRedisStreamPublisher.publish(message)
    }
}
