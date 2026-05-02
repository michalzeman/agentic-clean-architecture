package mz.bank.transaction.adapter.nats.jetstream.outbound

import mz.bank.transaction.domain.BankTransactionEvent
import org.springframework.context.annotation.Profile
import org.springframework.integration.annotation.ServiceActivator
import org.springframework.stereotype.Component

/**
 * ServiceActivator for routing outbound bank transaction domain events to JetStream.
 */
@Component
@Profile("nats-jetstream")
class BankTransactionOutboundEventHandler(
    private val publisher: JetStreamBankTransactionEventsPublisher,
) {
    @ServiceActivator(inputChannel = "outboundBankTransactionJetStreamChannel", async = "true")
    suspend fun handleOutboundEvent(event: BankTransactionEvent) {
        publisher.publish(event)
    }
}
