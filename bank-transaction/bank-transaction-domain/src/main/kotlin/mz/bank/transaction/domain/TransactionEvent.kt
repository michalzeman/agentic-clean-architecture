package mz.bank.transaction.domain

import mz.shared.domain.AggregateId
import java.math.BigDecimal
import java.time.Instant

/**
 * Domain events for Transaction aggregate.
 * These events represent state changes and are used for event sourcing.
 */
sealed class TransactionEvent {
    abstract val aggregateId: AggregateId
    abstract val correlationId: String
    abstract val updatedAt: Instant

    /**
     * Transaction was initialized.
     */
    data class TransactionCreated(
        override val aggregateId: AggregateId,
        override val correlationId: String,
        override val updatedAt: Instant,
        val fromAccountId: AggregateId,
        val toAccountId: AggregateId,
        val amount: BigDecimal,
    ) : TransactionEvent()

    /**
     * Money was successfully withdrawn from source account.
     */
    data class TransactionMoneyWithdrawn(
        override val aggregateId: AggregateId,
        override val correlationId: String,
        override val updatedAt: Instant,
    ) : TransactionEvent()

    /**
     * Money was successfully deposited to destination account.
     */
    data class TransactionMoneyDeposited(
        override val aggregateId: AggregateId,
        override val correlationId: String,
        override val updatedAt: Instant,
    ) : TransactionEvent()

    /**
     * Transaction completed successfully.
     */
    data class TransactionFinished(
        override val aggregateId: AggregateId,
        override val correlationId: String,
        override val updatedAt: Instant,
        val fromAccountId: AggregateId,
        val toAccountId: AggregateId,
    ) : TransactionEvent()

    /**
     * Transaction failed and needs rollback.
     */
    data class TransactionFailed(
        override val aggregateId: AggregateId,
        override val correlationId: String,
        override val updatedAt: Instant,
        val reason: String,
    ) : TransactionEvent()

    /**
     * Transaction was rolled back.
     */
    data class TransactionRolledBack(
        override val aggregateId: AggregateId,
        override val correlationId: String,
        override val updatedAt: Instant,
        val fromAccountId: AggregateId,
        val toAccountId: AggregateId,
        val amount: BigDecimal,
    ) : TransactionEvent()

    /**
     * Withdraw phase was rolled back.
     */
    data class TransactionWithdrawRolledBack(
        override val aggregateId: AggregateId,
        override val correlationId: String,
        override val updatedAt: Instant,
    ) : TransactionEvent()

    /**
     * Deposit phase was rolled back.
     */
    data class TransactionDepositRolledBack(
        override val aggregateId: AggregateId,
        override val correlationId: String,
        override val updatedAt: Instant,
    ) : TransactionEvent()
}
