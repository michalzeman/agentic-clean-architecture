package mz.bank.account.application

import mz.bank.account.domain.BankAccount
import mz.bank.account.domain.BankAccountAggregate
import mz.bank.account.domain.BankAccountCommand
import mz.shared.domain.LockProvider
import org.springframework.stereotype.Component

/**
 * Command handler for BankAccount aggregate.
 * Processes domain commands and persists aggregate state changes.
 */
@Component
class BankAccountCommandHandler(
    private val bankAccountRepository: BankAccountRepository,
    private val lockProvider: LockProvider,
    private val createBankAccountUseCase: CreateBankAccountUseCase,
) {
    /**
     * Handles a BankAccountCommand and returns the updated BankAccount.
     * Uses distributed locking to ensure consistency.
     */
    suspend fun handle(command: BankAccountCommand): BankAccount =
        when (command) {
            is BankAccountCommand.CreateAccount -> handleCreate(command)
            is BankAccountCommand.DepositMoney -> handleDeposit(command)
            is BankAccountCommand.WithdrawMoney -> handleWithdraw(command)
            is BankAccountCommand.WithdrawForTransfer -> handleWithdrawForTransfer(command)
            is BankAccountCommand.DepositFromTransfer -> handleDepositFromTransfer(command)
            is BankAccountCommand.FinishTransaction -> handleFinishTransaction(command)
        }

    private suspend fun handleCreate(command: BankAccountCommand.CreateAccount): BankAccount =
        lockProvider.withLock(command.email.value) {
            createBankAccountUseCase.execute(command)
        }

    private suspend fun handleDeposit(command: BankAccountCommand.DepositMoney): BankAccount =
        lockProvider.withLock(command.aggregateId.value) {
            val account = findAccountOrThrow(command.aggregateId)
            val aggregate = BankAccountAggregate(account).deposit(command)
            bankAccountRepository.upsert(aggregate)
        }

    private suspend fun handleWithdraw(command: BankAccountCommand.WithdrawMoney): BankAccount =
        lockProvider.withLock(command.aggregateId.value) {
            val account = findAccountOrThrow(command.aggregateId)
            val aggregate = BankAccountAggregate(account).withdraw(command)
            bankAccountRepository.upsert(aggregate)
        }

    private suspend fun handleWithdrawForTransfer(command: BankAccountCommand.WithdrawForTransfer): BankAccount =
        lockProvider.withLock(command.aggregateId.value) {
            val account = findAccountOrThrow(command.aggregateId)
            val aggregate = BankAccountAggregate(account).withdrawForTransfer(command)
            bankAccountRepository.upsert(aggregate)
        }

    private suspend fun handleDepositFromTransfer(command: BankAccountCommand.DepositFromTransfer): BankAccount =
        lockProvider.withLock(command.aggregateId.value) {
            val account = findAccountOrThrow(command.aggregateId)
            val aggregate = BankAccountAggregate(account).depositFromTransfer(command)
            bankAccountRepository.upsert(aggregate)
        }

    private suspend fun handleFinishTransaction(command: BankAccountCommand.FinishTransaction): BankAccount =
        lockProvider.withLock(command.aggregateId.value) {
            val account = findAccountOrThrow(command.aggregateId)
            val aggregate = BankAccountAggregate(account).finishTransaction(command)
            bankAccountRepository.upsert(aggregate)
        }

    private suspend fun findAccountOrThrow(aggregateId: mz.shared.domain.AggregateId): BankAccount =
        bankAccountRepository.findById(aggregateId)
            ?: throw IllegalArgumentException("BankAccount with id ${aggregateId.value} not found")
}
