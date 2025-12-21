package mz.bank.transaction.domain

import mz.shared.domain.AggregateId
import java.math.BigDecimal

/**
 * Domain commands for BankTransaction aggregate.
 * Commands represent actions that change the state of the aggregate.
 */
sealed class BankTransactionCommand {
    abstract val correlationId: String

    /**
     * Create a new money transfer transaction.
     */
    data class CreateBankTransaction(
        override val correlationId: String,
        val fromAccountId: AggregateId,
        val toAccountId: AggregateId,
        val amount: BigDecimal,
    ) : BankTransactionCommand()

    /**
     * Validate that money was withdrawn from source account.
     */
    data class ValidateBankTransactionMoneyWithdraw(
        val aggregateId: AggregateId,
        override val correlationId: String,
    ) : BankTransactionCommand()

    /**
     * Validate that money was deposited to destination account.
     */
    data class ValidateBankTransactionMoneyDeposit(
        val aggregateId: AggregateId,
        override val correlationId: String,
    ) : BankTransactionCommand()

    /**
     * Complete the transaction after both withdraw and deposit are done.
     */
    data class FinishBankTransaction(
        val aggregateId: AggregateId,
        override val correlationId: String,
        val fromAccountId: AggregateId,
        val toAccountId: AggregateId,
    ) : BankTransactionCommand()

    /**
     * Cancel and rollback the transaction.
     */
    data class CancelBankTransaction(
        val aggregateId: AggregateId,
        override val correlationId: String,
        val fromAccountId: AggregateId,
        val toAccountId: AggregateId,
        val amount: BigDecimal,
    ) : BankTransactionCommand()
}
