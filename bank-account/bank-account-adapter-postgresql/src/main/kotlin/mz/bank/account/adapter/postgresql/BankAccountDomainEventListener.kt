package mz.bank.account.adapter.postgresql

import mz.bank.account.domain.BankAccountEvent
import mz.shared.domain.AGGREGATE_ID
import org.springframework.context.annotation.Profile
import org.springframework.context.event.EventListener
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.support.MessageBuilder
import org.springframework.stereotype.Component

/**
 * Domain event listener for BankAccount aggregate.
 * Listens for domain events published via Spring Data's @DomainEvents mechanism
 * and forwards them to the bank account domain events channel.
 * Active when the 'postgres-persistence' Spring profile is enabled.
 */
@Component
@Profile("postgres-persistence")
class BankAccountDomainEventListener(
    private val bankAccountDomainEventsChannel: MessageChannel,
) {
    @EventListener
    fun handle(event: BankAccountEvent) {
        val message =
            MessageBuilder
                .withPayload(event)
                .setHeader(AGGREGATE_ID, event.aggregateId.value)
                .build()
        bankAccountDomainEventsChannel.send(message)
    }
}
