package mz.bank.account.application.integration.inbound

import mz.bank.account.domain.BankAccountCommand

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
 * Converts TransactionRolledBack event to DepositMoney command.
 * Refunds money to the source account when a transaction is rolled back after withdrawal.
 *
 * Field mapping:
 * - event.fromAccountId → command.aggregateId
 * - event.amount → command.amount
 */
fun InboundBankTransactionEvent.TransactionRolledBack.toDepositMoney(): BankAccountCommand.DepositMoney =
    BankAccountCommand.DepositMoney(
        aggregateId = fromAccountId,
        amount = amount,
    )

/**
 * Converts TransactionRolledBack event to WithdrawMoney command.
 * Reverses money from the destination account when a transaction is rolled back after deposit.
 *
 * Field mapping:
 * - event.toAccountId → command.aggregateId
 * - event.amount → command.amount
 */
fun InboundBankTransactionEvent.TransactionRolledBack.toWithdrawMoney(): BankAccountCommand.WithdrawMoney =
    BankAccountCommand.WithdrawMoney(
        aggregateId = toAccountId,
        amount = amount,
    )

/**
 * Converts TransactionWithdrawRolledBack event to DepositMoney command.
 * Refunds money to the source account when withdrawal is rolled back.
 *
 * Field mapping:
 * - event.fromAccountId → command.aggregateId
 * - event.amount → command.amount
 */
fun InboundBankTransactionEvent.TransactionWithdrawRolledBack.toDepositMoney(): BankAccountCommand.DepositMoney =
    BankAccountCommand.DepositMoney(
        aggregateId = fromAccountId,
        amount = amount,
    )

/**
 * Converts TransactionDepositRolledBack event to WithdrawMoney command.
 * Reverses money from the destination account when deposit is rolled back.
 *
 * Field mapping:
 * - event.toAccountId → command.aggregateId
 * - event.amount → command.amount
 */
fun InboundBankTransactionEvent.TransactionDepositRolledBack.toWithdrawMoney(): BankAccountCommand.WithdrawMoney =
    BankAccountCommand.WithdrawMoney(
        aggregateId = toAccountId,
        amount = amount,
    )
