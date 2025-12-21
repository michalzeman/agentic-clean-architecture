package mz.bank.transaction.application

import mz.bank.transaction.domain.BankTransaction
import mz.bank.transaction.domain.BankTransactionAggregate
import mz.bank.transaction.domain.BankTransactionCommand
import mz.shared.domain.LockProvider
import org.springframework.stereotype.Component

/**
 * Command handler for BankTransaction aggregate.
 * Processes domain commands and persists aggregate state changes.
 */
@Component
class BankTransactionCommandHandler(
    private val bankTransactionRepository: BankTransactionRepository,
    private val lockProvider: LockProvider,
) {
    /**
     * Handles a BankTransactionCommand and returns the updated BankTransaction.
     * Uses distributed locking to ensure consistency.
     */
    suspend fun handle(command: BankTransactionCommand): BankTransaction =
        when (command) {
            is BankTransactionCommand.CreateBankTransaction -> handleCreate(command)
            is BankTransactionCommand.ValidateBankTransactionMoneyWithdraw -> handleValidateWithdraw(command)
            is BankTransactionCommand.ValidateBankTransactionMoneyDeposit -> handleValidateDeposit(command)
            is BankTransactionCommand.FinishBankTransaction -> handleFinish(command)
            is BankTransactionCommand.CancelBankTransaction -> handleCancel(command)
        }

    private suspend fun handleCreate(command: BankTransactionCommand.CreateBankTransaction): BankTransaction =
        lockProvider.withLock(command.correlationId) {
            val aggregate = BankTransactionAggregate.create(command)
            bankTransactionRepository.upsert(aggregate)
        }

    private suspend fun handleValidateWithdraw(command: BankTransactionCommand.ValidateBankTransactionMoneyWithdraw): BankTransaction =
        lockProvider.withLock(command.aggregateId.value) {
            val bankTransaction = findBankTransactionOrThrow(command.aggregateId)
            val aggregate = BankTransactionAggregate(bankTransaction).validateMoneyWithdraw(command)
            bankTransactionRepository.upsert(aggregate)
        }

    private suspend fun handleValidateDeposit(command: BankTransactionCommand.ValidateBankTransactionMoneyDeposit): BankTransaction =
        lockProvider.withLock(command.aggregateId.value) {
            val bankTransaction = findBankTransactionOrThrow(command.aggregateId)
            val aggregate = BankTransactionAggregate(bankTransaction).validateMoneyDeposit(command)
            bankTransactionRepository.upsert(aggregate)
        }

    private suspend fun handleFinish(command: BankTransactionCommand.FinishBankTransaction): BankTransaction =
        lockProvider.withLock(command.aggregateId.value) {
            val bankTransaction = findBankTransactionOrThrow(command.aggregateId)
            val aggregate = BankTransactionAggregate(bankTransaction).finishBankTransaction(command)
            bankTransactionRepository.upsert(aggregate)
        }

    private suspend fun handleCancel(command: BankTransactionCommand.CancelBankTransaction): BankTransaction =
        lockProvider.withLock(command.aggregateId.value) {
            val bankTransaction = findBankTransactionOrThrow(command.aggregateId)
            val aggregate = BankTransactionAggregate(bankTransaction).cancelBankTransaction(command)
            bankTransactionRepository.upsert(aggregate)
        }

    private suspend fun findBankTransactionOrThrow(aggregateId: mz.shared.domain.AggregateId): BankTransaction =
        bankTransactionRepository.findById(aggregateId)
            ?: throw IllegalArgumentException("BankTransaction with id ${aggregateId.value} not found")
}
