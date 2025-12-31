package mz.bank.transaction.application.account

import mz.shared.domain.AggregateId
import java.math.BigDecimal

sealed interface AccountEvent {
    /**
     * Event representing that an account was created in the bank-account service.
     * This is a simple application event used for internal messaging.
     */
    data class AccountCreatedEvent(
        val accountId: AggregateId,
    ) : AccountEvent

    /**
     * Event representing that a transfer withdrawal was started on an account.
     * Triggered when the bank-account service starts withdrawing money for a transfer.
     */
    data class TransferWithdrawalStartedEvent(
        val accountId: AggregateId,
        val transactionId: String,
        val amount: BigDecimal,
    ) : AccountEvent

    /**
     * Event representing that a transfer deposit was started on an account.
     * Triggered when the bank-account service starts depositing money from a transfer.
     */
    data class TransferDepositStartedEvent(
        val accountId: AggregateId,
        val transactionId: String,
        val amount: BigDecimal,
    ) : AccountEvent

    /**
     * Event representing that a transaction was finished on an account.
     * Triggered when the bank-account service completes its part of the transaction.
     */
    data class TransactionFinishedEvent(
        val accountId: AggregateId,
        val transactionId: String,
    ) : AccountEvent

    /**
     * Event representing that a transfer withdrawal was rolled back on an account.
     * Triggered during compensation when a withdrawal needs to be reverted.
     */
    data class TransferWithdrawalRolledBackEvent(
        val accountId: AggregateId,
        val transactionId: String,
        val amount: BigDecimal,
    ) : AccountEvent

    /**
     * Event representing that a transfer deposit was rolled back on an account.
     * Triggered during compensation when a deposit needs to be reverted.
     */
    data class TransferDepositRolledBackEvent(
        val accountId: AggregateId,
        val transactionId: String,
        val amount: BigDecimal,
    ) : AccountEvent

    object DefaultAccountEvent : AccountEvent
}
