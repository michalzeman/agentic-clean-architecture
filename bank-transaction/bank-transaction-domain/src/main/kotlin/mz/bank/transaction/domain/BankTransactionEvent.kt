package mz.bank.transaction.domain

import mz.shared.domain.AggregateId
import java.math.BigDecimal
import java.time.Instant

/**
 * Domain events for BankTransaction aggregate.
 * These events represent state changes and are used for event sourcing.
 */
sealed class BankTransactionEvent {
    abstract val aggregateId: AggregateId
    abstract val correlationId: String
    abstract val updatedAt: Instant

    /**
     * BankTransaction was initialized.
     */
    data class BankTransactionCreated(
        override val aggregateId: AggregateId,
        override val correlationId: String,
        override val updatedAt: Instant,
        val fromAccountId: AggregateId,
        val toAccountId: AggregateId,
        val amount: BigDecimal,
    ) : BankTransactionEvent()

    /**
     * Money was successfully withdrawn from source account.
     */
    data class BankTransactionMoneyWithdrawn(
        override val aggregateId: AggregateId,
        override val correlationId: String,
        override val updatedAt: Instant,
        val accountId: AggregateId,
    ) : BankTransactionEvent()

    /**
     * Money was successfully deposited to destination account.
     */
    data class BankTransactionMoneyDeposited(
        override val aggregateId: AggregateId,
        override val correlationId: String,
        override val updatedAt: Instant,
    ) : BankTransactionEvent()

    /**
     * BankTransaction completed successfully.
     */
    data class BankTransactionFinished(
        override val aggregateId: AggregateId,
        override val correlationId: String,
        override val updatedAt: Instant,
        val fromAccountId: AggregateId,
        val toAccountId: AggregateId,
    ) : BankTransactionEvent()

    /**
     * BankTransaction failed and needs rollback.
     */
    data class BankTransactionFailed(
        override val aggregateId: AggregateId,
        override val correlationId: String,
        override val updatedAt: Instant,
        val reason: String,
    ) : BankTransactionEvent()

    /**
     * BankTransaction was rolled back.
     */
    data class BankTransactionRolledBack(
        override val aggregateId: AggregateId,
        override val correlationId: String,
        override val updatedAt: Instant,
        val fromAccountId: AggregateId,
        val toAccountId: AggregateId,
        val amount: BigDecimal,
    ) : BankTransactionEvent()

    /**
     * Withdraw phase was rolled back.
     */
    data class BankTransactionWithdrawRolledBack(
        override val aggregateId: AggregateId,
        override val correlationId: String,
        override val updatedAt: Instant,
    ) : BankTransactionEvent()

    /**
     * Deposit phase was rolled back.
     */
    data class BankTransactionDepositRolledBack(
        override val aggregateId: AggregateId,
        override val correlationId: String,
        override val updatedAt: Instant,
    ) : BankTransactionEvent()
}
