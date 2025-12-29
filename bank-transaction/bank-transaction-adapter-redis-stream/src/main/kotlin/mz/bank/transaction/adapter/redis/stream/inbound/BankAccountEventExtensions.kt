package mz.bank.transaction.adapter.redis.stream.inbound

import mz.bank.account.contract.proto.BankAccountEvent
import mz.bank.transaction.application.account.AccountEvent
import mz.shared.domain.AggregateId

/**
 * Converts a protobuf BankAccountEvent to an application AccountCreatedEvent.
 * Returns null if the event is not an AccountCreated event.
 */
fun BankAccountEvent.toAccountEvent(): AccountEvent =
    when {
        hasAccountCreated() -> {
            val accountCreated = accountCreated
            AccountEvent.AccountCreatedEvent(
                accountId = AggregateId(accountCreated.aggregateId),
            )
        }

        else -> AccountEvent.DefaultAccountEvent
    }
