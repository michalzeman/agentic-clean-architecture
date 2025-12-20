package mz.bank.transaction.domain

import mz.shared.domain.AggregateId
import java.math.BigDecimal

/**
 * Domain commands for Transaction aggregate.
 * Commands represent actions that change the state of the aggregate.
 */
sealed class TransactionCommand {
    abstract val correlationId: String

    /**
     * Create a new money transfer transaction.
     */
    data class CreateTransaction(
        override val correlationId: String,
        val fromAccountId: AggregateId,
        val toAccountId: AggregateId,
        val amount: BigDecimal,
    ) : TransactionCommand()

    /**
     * Validate that money was withdrawn from source account.
     */
    data class ValidateTransactionMoneyWithdraw(
        val aggregateId: AggregateId,
        override val correlationId: String,
    ) : TransactionCommand()

    /**
     * Validate that money was deposited to destination account.
     */
    data class ValidateTransactionMoneyDeposit(
        val aggregateId: AggregateId,
        override val correlationId: String,
    ) : TransactionCommand()

    /**
     * Complete the transaction after both withdraw and deposit are done.
     */
    data class FinishTransaction(
        val aggregateId: AggregateId,
        override val correlationId: String,
        val fromAccountId: AggregateId,
        val toAccountId: AggregateId,
    ) : TransactionCommand()

    /**
     * Cancel and rollback the transaction.
     */
    data class CancelTransaction(
        val aggregateId: AggregateId,
        override val correlationId: String,
        val fromAccountId: AggregateId,
        val toAccountId: AggregateId,
        val amount: BigDecimal,
    ) : TransactionCommand()
}
