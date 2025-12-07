package mz.bank.account.adapter.redis.stream

import mz.shared.connector.redis.RedisStreamPublisher
import org.apache.commons.logging.LogFactory
import org.springframework.integration.annotation.Poller
import org.springframework.integration.annotation.ServiceActivator
import org.springframework.messaging.Message
import org.springframework.stereotype.Component
import mz.bank.account.contract.proto.BankAccountEvent as ProtoBankAccountEvent

private val logger = LogFactory.getLog(RedisStreamBankAccountEventsPublisher::class.java)

/**
 * Service activator that publishes bank account protobuf events to Redis stream.
 * Listens to the outboundBankAccountRedisStreamChannel and publishes protobuf events
 * to the configured Redis stream.
 */
@Component
class RedisStreamBankAccountEventsPublisher(
    private val bankAccountEventsRedisStreamPublisher: RedisStreamPublisher<ProtoBankAccountEvent>,
) {
    @ServiceActivator(
        inputChannel = "outboundBankAccountRedisStreamChannel",
        requiresReply = "false",
        poller = Poller(fixedDelay = "100"),
        async = "true",
    )
    suspend fun publish(message: Message<ProtoBankAccountEvent>) {
        val event = message.payload
        logger.info("Publishing bank account protobuf event to Redis stream $event")

        bankAccountEventsRedisStreamPublisher.publish(message)
    }
}
