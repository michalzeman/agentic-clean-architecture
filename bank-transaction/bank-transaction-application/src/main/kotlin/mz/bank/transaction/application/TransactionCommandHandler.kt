package mz.bank.transaction.application

import mz.bank.transaction.domain.Transaction
import mz.bank.transaction.domain.TransactionAggregate
import mz.bank.transaction.domain.TransactionCommand
import mz.shared.domain.LockProvider
import org.springframework.stereotype.Component

/**
 * Command handler for Transaction aggregate.
 * Processes domain commands and persists aggregate state changes.
 */
@Component
class TransactionCommandHandler(
    private val transactionRepository: TransactionRepository,
    private val lockProvider: LockProvider,
) {
    /**
     * Handles a TransactionCommand and returns the updated Transaction.
     * Uses distributed locking to ensure consistency.
     */
    suspend fun handle(command: TransactionCommand): Transaction =
        when (command) {
            is TransactionCommand.CreateTransaction -> handleCreate(command)
            is TransactionCommand.ValidateTransactionMoneyWithdraw -> handleValidateWithdraw(command)
            is TransactionCommand.ValidateTransactionMoneyDeposit -> handleValidateDeposit(command)
            is TransactionCommand.FinishTransaction -> handleFinish(command)
            is TransactionCommand.CancelTransaction -> handleCancel(command)
        }

    private suspend fun handleCreate(command: TransactionCommand.CreateTransaction): Transaction =
        lockProvider.withLock(command.correlationId) {
            val aggregate = TransactionAggregate.create(command)
            transactionRepository.upsert(aggregate)
        }

    private suspend fun handleValidateWithdraw(command: TransactionCommand.ValidateTransactionMoneyWithdraw): Transaction =
        lockProvider.withLock(command.aggregateId.value) {
            val transaction = findTransactionOrThrow(command.aggregateId)
            val aggregate = TransactionAggregate(transaction).validateMoneyWithdraw(command)
            transactionRepository.upsert(aggregate)
        }

    private suspend fun handleValidateDeposit(command: TransactionCommand.ValidateTransactionMoneyDeposit): Transaction =
        lockProvider.withLock(command.aggregateId.value) {
            val transaction = findTransactionOrThrow(command.aggregateId)
            val aggregate = TransactionAggregate(transaction).validateMoneyDeposit(command)
            transactionRepository.upsert(aggregate)
        }

    private suspend fun handleFinish(command: TransactionCommand.FinishTransaction): Transaction =
        lockProvider.withLock(command.aggregateId.value) {
            val transaction = findTransactionOrThrow(command.aggregateId)
            val aggregate = TransactionAggregate(transaction).finishTransaction(command)
            transactionRepository.upsert(aggregate)
        }

    private suspend fun handleCancel(command: TransactionCommand.CancelTransaction): Transaction =
        lockProvider.withLock(command.aggregateId.value) {
            val transaction = findTransactionOrThrow(command.aggregateId)
            val aggregate = TransactionAggregate(transaction).cancelTransaction(command)
            transactionRepository.upsert(aggregate)
        }

    private suspend fun findTransactionOrThrow(aggregateId: mz.shared.domain.AggregateId): Transaction =
        transactionRepository.findById(aggregateId)
            ?: throw IllegalArgumentException("Transaction with id ${aggregateId.value} not found")
}
