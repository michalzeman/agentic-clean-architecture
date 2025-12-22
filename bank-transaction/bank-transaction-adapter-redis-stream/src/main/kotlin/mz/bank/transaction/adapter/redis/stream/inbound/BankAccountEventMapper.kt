package mz.bank.transaction.adapter.redis.stream.inbound

import mz.bank.account.contract.proto.BankAccountEvent
import mz.bank.transaction.application.integration.AccountCreatedEvent
import mz.shared.domain.AggregateId

/**
 * Mapper for converting protobuf bank account events to application events.
 */
object BankAccountEventMapper {
    /**
     * Converts a protobuf BankAccountEvent to an application AccountCreatedEvent.
     * Returns null if the event is not an AccountCreated event.
     */
    fun toAccountCreatedEvent(protoEvent: BankAccountEvent): AccountCreatedEvent? =
        when {
            protoEvent.hasAccountCreated() -> {
                val accountCreated = protoEvent.accountCreated
                AccountCreatedEvent(
                    accountId = AggregateId(accountCreated.aggregateId),
                )
            }
            else -> null
        }
}
