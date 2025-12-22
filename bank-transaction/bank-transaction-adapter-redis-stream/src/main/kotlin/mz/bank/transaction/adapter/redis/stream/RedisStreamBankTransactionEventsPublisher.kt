package mz.bank.transaction.adapter.redis.stream

import mz.shared.connector.redis.RedisStreamPublisher
import org.apache.commons.logging.LogFactory
import org.springframework.integration.annotation.Poller
import org.springframework.integration.annotation.ServiceActivator
import org.springframework.messaging.Message
import org.springframework.stereotype.Component
import mz.bank.transaction.contract.proto.BankTransactionEvent as ProtoBankTransactionEvent

private val logger = LogFactory.getLog(RedisStreamBankTransactionEventsPublisher::class.java)

/**
 * Service activator that publishes bank transaction protobuf events to Redis stream.
 * Listens to the outboundBankTransactionRedisStreamChannel and publishes protobuf events
 * to the configured Redis stream.
 */
@Component
class RedisStreamBankTransactionEventsPublisher(
    private val bankTransactionEventsRedisStreamPublisher: RedisStreamPublisher<ProtoBankTransactionEvent>,
) {
    @ServiceActivator(
        inputChannel = "outboundBankTransactionRedisStreamChannel",
        requiresReply = "false",
        poller = Poller(fixedDelay = "100"),
        async = "true",
    )
    suspend fun publish(message: Message<ProtoBankTransactionEvent>) {
        val event = message.payload
        logger.info("Publishing bank transaction protobuf event to Redis stream $event")

        bankTransactionEventsRedisStreamPublisher.publish(message)
    }
}
