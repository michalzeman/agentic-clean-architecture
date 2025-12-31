package mz.bank.account.application.transaction

import mz.bank.account.domain.BankAccountCommand

fun InboundBankTransactionEvent.toCommand(): BankAccountCommand =
    when (this) {
        is InboundBankTransactionEvent.TransactionCreated -> this.toWithdrawForTransfer()
        is InboundBankTransactionEvent.TransactionMoneyWithdrawn -> this.toDepositFromTransfer()
        is InboundBankTransactionEvent.TransactionMoneyDeposited -> this.toFinishTransaction()
        is InboundBankTransactionEvent.TransactionWithdrawRolledBack -> this.toRollbackWithdrawForTransfer()
        is InboundBankTransactionEvent.TransactionDepositRolledBack -> this.toRollbackDepositFromTransfer()
        is InboundBankTransactionEvent.TransactionFinished -> this.toTransactionFinishedFromAccountId()
        else -> BankAccountCommand.NoOp
    }

fun InboundBankTransactionEvent.TransactionFinished.toTransactionFinishedFromAccountId(): BankAccountCommand.FinishTransactions {
    val fromAccountCommand =
        BankAccountCommand.FinishTransaction(
            aggregateId = fromAccountId,
            transactionId = aggregateId.value,
        )

    val toAccountCommand =
        BankAccountCommand.FinishTransaction(
            aggregateId = toAccountId,
            transactionId = aggregateId.value,
        )

    return BankAccountCommand.FinishTransactions(setOf(fromAccountCommand, toAccountCommand))
}

/**
 * Converts TransactionCreated event to WithdrawForTransfer command (Phase 1).
 * Initiates the withdrawal from the source account.
 *
 * Field mapping:
 * - event.fromAccountId → command.aggregateId (target account)
 * - event.aggregateId.value → command.transactionId
 * - event.amount → command.amount
 */
fun InboundBankTransactionEvent.TransactionCreated.toWithdrawForTransfer(): BankAccountCommand.WithdrawForTransfer =
    BankAccountCommand.WithdrawForTransfer(
        aggregateId = fromAccountId,
        transactionId = aggregateId.value,
        amount = amount,
    )

/**
 * Converts TransactionMoneyWithdrawn event to DepositFromTransfer command (Phase 2).
 * Initiates the deposit to the destination account after successful withdrawal validation.
 *
 * This is triggered by the bank-transaction service after it validates the withdrawal.
 * It advances the saga to the deposit phase.
 *
 * Field mapping:
 * - event.toAccountId → command.aggregateId (target account)
 * - event.aggregateId.value → command.transactionId
 * - event.amount → command.amount
 */
fun InboundBankTransactionEvent.TransactionMoneyWithdrawn.toDepositFromTransfer(): BankAccountCommand.DepositFromTransfer =
    BankAccountCommand.DepositFromTransfer(
        aggregateId = toAccountId,
        transactionId = aggregateId.value,
        amount = amount,
    )

/**
 * Converts TransactionMoneyDeposited event to FinishTransaction command (Phase 3).
 * Completes the transaction on both accounts after successful deposit validation.
 *
 * This is triggered by the bank-transaction service after it validates the deposit.
 * It advances the saga to the finish phase.
 *
 * Field mapping:
 * - event.accountId → command.aggregateId (the account to finish)
 * - event.aggregateId.value → command.transactionId
 */
fun InboundBankTransactionEvent.TransactionMoneyDeposited.toFinishTransaction(): BankAccountCommand.FinishTransaction =
    BankAccountCommand.FinishTransaction(
        aggregateId = accountId,
        transactionId = aggregateId.value,
    )

/**
 * Converts TransactionWithdrawRolledBack event to RollbackWithdrawForTransfer command.
 * Refunds money to the source account when withdrawal is rolled back.
 * Uses transaction-aware rollback that validates against openedTransactions.
 *
 * Field mapping:
 * - event.fromAccountId → command.aggregateId
 * - event.aggregateId.value → command.transactionId
 * - event.amount → command.amount
 */
fun InboundBankTransactionEvent.TransactionWithdrawRolledBack.toRollbackWithdrawForTransfer():
    BankAccountCommand.RollbackWithdrawForTransfer =
    BankAccountCommand.RollbackWithdrawForTransfer(
        aggregateId = fromAccountId,
        transactionId = aggregateId.value,
        amount = amount,
    )

/**
 * Converts TransactionDepositRolledBack event to RollbackDepositFromTransfer command.
 * Reverses money from the destination account when deposit is rolled back.
 * Uses transaction-aware rollback that validates against openedTransactions
 * and does not enforce balance checks.
 *
 * Field mapping:
 * - event.toAccountId → command.aggregateId
 * - event.aggregateId.value → command.transactionId
 * - event.amount → command.amount
 */
fun InboundBankTransactionEvent.TransactionDepositRolledBack.toRollbackDepositFromTransfer():
    BankAccountCommand.RollbackDepositFromTransfer =
    BankAccountCommand.RollbackDepositFromTransfer(
        aggregateId = toAccountId,
        transactionId = aggregateId.value,
        amount = amount,
    )
