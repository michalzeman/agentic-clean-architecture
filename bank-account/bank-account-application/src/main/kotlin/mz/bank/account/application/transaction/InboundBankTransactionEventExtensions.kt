package mz.bank.account.application.transaction

import mz.bank.account.domain.BankAccountCommand

fun InboundBankTransactionEvent.toCommand(): BankAccountCommand =
    when (this) {
        is InboundBankTransactionEvent.TransactionCreated -> this.toWithdrawForTransfer()
        is InboundBankTransactionEvent.TransactionWithdrawRolledBack -> this.toRollbackWithdrawForTransfer()
        is InboundBankTransactionEvent.TransactionDepositRolledBack -> this.toRollbackDepositFromTransfer()
        else -> BankAccountCommand.NoOp
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
 * Converts TransactionCreated event to DepositFromTransfer command (Phase 2).
 * Initiates the deposit to the destination account.
 *
 * Note: This should be triggered only after successful withdrawal.
 * In a saga pattern, this would be called in response to a TransferWithdrawalStarted event
 * or BankTransactionMoneyWithdrawn event.
 *
 * Field mapping:
 * - event.toAccountId → command.aggregateId (target account)
 * - event.aggregateId.value → command.transactionId
 * - event.amount → command.amount
 */
fun InboundBankTransactionEvent.TransactionCreated.toDepositFromTransfer(): BankAccountCommand.DepositFromTransfer =
    BankAccountCommand.DepositFromTransfer(
        aggregateId = toAccountId,
        transactionId = aggregateId.value,
        amount = amount,
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
