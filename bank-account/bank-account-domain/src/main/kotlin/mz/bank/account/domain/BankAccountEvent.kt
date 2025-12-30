package mz.bank.account.domain

import mz.shared.domain.AggregateId
import java.math.BigDecimal
import java.time.Instant

/**
 * Domain events for BankAccount aggregate.
 * These events represent state changes and are used for event sourcing.
 */
sealed class BankAccountEvent {
    abstract val aggregateId: AggregateId
    abstract val updatedAt: Instant

    data class AccountCreated(
        override val aggregateId: AggregateId,
        override val updatedAt: Instant,
        val email: Email,
        val initialBalance: BigDecimal,
    ) : BankAccountEvent()

    data class MoneyDeposited(
        override val aggregateId: AggregateId,
        override val updatedAt: Instant,
        val amount: BigDecimal,
    ) : BankAccountEvent()

    data class MoneyWithdrawn(
        override val aggregateId: AggregateId,
        override val updatedAt: Instant,
        val amount: BigDecimal,
    ) : BankAccountEvent()

    data class TransferWithdrawalStarted(
        override val aggregateId: AggregateId,
        override val updatedAt: Instant,
        val transactionId: String,
        val amount: BigDecimal,
    ) : BankAccountEvent()

    data class TransferDepositStarted(
        override val aggregateId: AggregateId,
        override val updatedAt: Instant,
        val transactionId: String,
        val amount: BigDecimal,
    ) : BankAccountEvent()

    data class TransactionFinished(
        override val aggregateId: AggregateId,
        override val updatedAt: Instant,
        val transactionId: String,
    ) : BankAccountEvent()

    data class TransferWithdrawalRolledBack(
        override val aggregateId: AggregateId,
        override val updatedAt: Instant,
        val transactionId: String,
        val amount: BigDecimal,
    ) : BankAccountEvent()

    data class TransferDepositRolledBack(
        override val aggregateId: AggregateId,
        override val updatedAt: Instant,
        val transactionId: String,
        val amount: BigDecimal,
    ) : BankAccountEvent()
}
