package mz.bank.transaction.adapter.redis

import mz.bank.transaction.domain.BankTransactionEvent
import mz.shared.domain.AGGREGATE_ID
import org.springframework.context.annotation.Profile
import org.springframework.context.event.EventListener
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.support.MessageBuilder
import org.springframework.stereotype.Component

/**
 * Domain event listener for BankTransaction aggregate.
 * Listens for domain events published via Spring Data's @DomainEvents mechanism
 * and forwards them to the bank transaction domain events channel.
 * Active when the 'redis-persistence' Spring profile is enabled.
 */
@Component
@Profile("redis-persistence")
class BankTransactionDomainEventListener(
    private val bankTransactionDomainEventsChannel: MessageChannel,
) {
    @EventListener
    fun handle(event: BankTransactionEvent) {
        val message =
            MessageBuilder
                .withPayload(event)
                .setHeader(AGGREGATE_ID, event.aggregateId.value)
                .build()
        bankTransactionDomainEventsChannel.send(message)
    }
}
