package mz.bank.account.adapter.nats.jetstream.inbound

import mz.bank.account.application.BankAccountCommandHandler
import mz.bank.account.application.transaction.toCommand
import mz.bank.transaction.contract.proto.BankTransactionEvent
import mz.shared.connector.nats.jetstream.consumer.JetStreamConsumer
import mz.shared.connector.nats.jetstream.consumer.JetStreamMessage
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

private val logger = LoggerFactory.getLogger(JetStreamBankTransactionConsumer::class.java)

/**
 * JetStream consumer that listens for bank transaction events from the bank-transaction service.
 * Converts protobuf events to domain commands and processes them.
 */
@Component
@Profile("nats-jetstream")
class JetStreamBankTransactionConsumer(
    private val jetStreamConsumer: JetStreamConsumer<BankTransactionEvent>,
    private val commandHandler: BankAccountCommandHandler, // TODO: change it to use sprint integration
) {
    /**
     * Handles the consumed JetStream message by converting it to a domain command
     * and passing it to the command handler.
     */
    suspend fun onMessage(message: JetStreamMessage<BankTransactionEvent>) {
        try {
            val inboundEvent = message.payload.toInboundEvent()
            val command = inboundEvent.toCommand()

            logger.info(
                "Received bank transaction event: type={}, aggregateId={}",
                inboundEvent::class.simpleName,
                inboundEvent.aggregateId,
            )

            commandHandler.handle(command)

            // Acknowledge the message after successful processing
            jetStreamConsumer.acknowledge(message)
        } catch (e: Exception) {
            logger.error("Failed to process bank transaction event", e)
            // Negative acknowledge to trigger redelivery
            jetStreamConsumer.negativeAcknowledge(message)
            throw e
        }
    }
}
