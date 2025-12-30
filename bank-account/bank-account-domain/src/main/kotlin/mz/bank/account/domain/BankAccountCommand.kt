package mz.bank.account.domain

import mz.shared.domain.AggregateId
import java.math.BigDecimal

/**
 * Domain commands for BankAccount aggregate.
 * Commands represent actions that change the state of the aggregate.
 */
sealed class BankAccountCommand {
    data class CreateAccount(
        val email: Email,
        val initialBalance: BigDecimal,
    ) : BankAccountCommand()

    data class DepositMoney(
        val aggregateId: AggregateId,
        val amount: BigDecimal,
    ) : BankAccountCommand()

    data class WithdrawMoney(
        val aggregateId: AggregateId,
        val amount: BigDecimal,
    ) : BankAccountCommand()

    data class WithdrawForTransfer(
        val aggregateId: AggregateId,
        val transactionId: String,
        val amount: BigDecimal,
    ) : BankAccountCommand()

    data class DepositFromTransfer(
        val aggregateId: AggregateId,
        val transactionId: String,
        val amount: BigDecimal,
    ) : BankAccountCommand()

    data class FinishTransaction(
        val aggregateId: AggregateId,
        val transactionId: String,
    ) : BankAccountCommand()

    data class RollbackWithdrawForTransfer(
        val aggregateId: AggregateId,
        val transactionId: String,
        val amount: BigDecimal,
    ) : BankAccountCommand()

    data class RollbackDepositFromTransfer(
        val aggregateId: AggregateId,
        val transactionId: String,
        val amount: BigDecimal,
    ) : BankAccountCommand()
}
