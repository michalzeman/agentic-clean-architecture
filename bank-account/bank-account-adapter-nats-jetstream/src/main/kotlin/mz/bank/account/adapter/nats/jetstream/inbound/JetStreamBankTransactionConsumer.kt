package mz.bank.account.adapter.nats.jetstream.inbound

import mz.bank.transaction.contract.proto.BankTransactionEvent
import mz.shared.connector.nats.jetstream.consumer.JetStreamConsumer
import mz.shared.connector.nats.jetstream.consumer.JetStreamMessage
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.support.MessageBuilder
import org.springframework.stereotype.Component

private val logger = LoggerFactory.getLogger(JetStreamBankTransactionConsumer::class.java)

/**
 * JetStream consumer that listens for bank transaction events from the bank-transaction service.
 * Converts protobuf events to domain events and routes them to the inbound Spring Integration channel.
 */
@Component
@Profile("nats-jetstream")
class JetStreamBankTransactionConsumer(
    private val jetStreamConsumer: JetStreamConsumer<BankTransactionEvent>,
    private val inboundBankTransactionEventsChannel: MessageChannel,
) {
    /**
     * Handles the consumed JetStream message by converting it to an InboundBankTransactionEvent
     * and sending it to the inboundBankTransactionEventsChannel for downstream Spring Integration processing.
     */
    suspend fun onMessage(message: JetStreamMessage<BankTransactionEvent>) {
        try {
            val inboundEvent = message.payload.toInboundEvent()

            logger.info(
                "Received bank transaction event: type={}, aggregateId={}",
                inboundEvent::class.simpleName,
                inboundEvent.aggregateId,
            )

            inboundBankTransactionEventsChannel.send(
                MessageBuilder.withPayload(inboundEvent).build(),
            )

            jetStreamConsumer.acknowledge(message)
        } catch (e: Exception) {
            logger.error("Failed to process bank transaction event", e)
            jetStreamConsumer.negativeAcknowledge(message)
            throw e
        }
    }
}
