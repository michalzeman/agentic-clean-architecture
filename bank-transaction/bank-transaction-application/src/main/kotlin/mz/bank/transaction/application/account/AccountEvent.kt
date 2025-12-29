package mz.bank.transaction.application.account

import mz.shared.domain.AggregateId

sealed interface AccountEvent {
    /**
     * Event representing that an account was created in the bank-account service.
     * This is a simple application event used for internal messaging.
     */
    data class AccountCreatedEvent(
        val accountId: AggregateId,
    ) : AccountEvent

    object DefaultAccountEvent : AccountEvent
}
