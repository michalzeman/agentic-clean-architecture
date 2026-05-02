package mz.bank.account.adapter.nats.jetstream.outbound

import mz.bank.account.domain.BankAccountEvent
import org.springframework.context.annotation.Profile
import org.springframework.integration.annotation.ServiceActivator
import org.springframework.stereotype.Component

/**
 * ServiceActivator for routing outbound bank account domain events to JetStream.
 */
@Component
@Profile("nats-jetstream")
class BankAccountOutboundEventHandler(
    private val publisher: JetStreamBankAccountEventsPublisher,
) {
    @ServiceActivator(inputChannel = "outboundBankAccountJetStreamChannel", async = "true")
    suspend fun handleOutboundEvent(event: BankAccountEvent) {
        publisher.publish(event)
    }
}
