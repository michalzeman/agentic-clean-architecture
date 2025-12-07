package mz.bank.account.adapter.redis.stream

import mz.bank.account.domain.BankAccountEvent
import mz.shared.connector.redis.RedisStreamPublisher
import mz.shared.domain.AGGREGATE_ID
import org.apache.commons.logging.LogFactory
import org.springframework.integration.annotation.Poller
import org.springframework.integration.annotation.ServiceActivator
import org.springframework.messaging.Message
import org.springframework.messaging.support.MessageBuilder
import org.springframework.stereotype.Component
import mz.bank.account.contract.proto.BankAccountEvent as ProtoBankAccountEvent

private val logger = LogFactory.getLog(RedisStreamBankAccountEventsPublisher::class.java)

/**
 * Service activator that publishes bank account domain events to Redis stream.
 * Listens to the outboundBankAccountRedisStreamChannel and publishes events
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
    suspend fun publish(message: Message<BankAccountEvent>) {
        val event = message.payload
        logger.info("Publishing bank account event to Redis stream: ${event::class.simpleName} for aggregate ${event.aggregateId}")

        // Map domain event to proto event
        val protoEvent = BankAccountEventMapper.toProto(event)

        val messageToPublish =
            MessageBuilder
                .withPayload(protoEvent)
                .copyHeaders(message.headers)
                .setHeaderIfAbsent(AGGREGATE_ID, event.aggregateId.value)
                .build()

        bankAccountEventsRedisStreamPublisher.publish(messageToPublish)
    }
}
