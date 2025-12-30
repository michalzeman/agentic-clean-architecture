package mz.bank.account.application

import mz.bank.account.domain.BankAccount
import mz.bank.account.domain.BankAccountAggregate
import mz.bank.account.domain.BankAccountCommand
import mz.shared.domain.LockProvider
import org.apache.commons.logging.LogFactory
import org.springframework.integration.annotation.Poller
import org.springframework.integration.annotation.ServiceActivator
import org.springframework.messaging.Message
import org.springframework.stereotype.Component

private val logger = LogFactory.getLog(BankAccountCommandHandler::class.java)

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
    suspend fun handle(command: BankAccountCommand): BankAccount? =
        when (command) {
            is BankAccountCommand.CreateAccount -> handleCreate(command)
            is BankAccountCommand.DepositMoney -> handleDeposit(command)
            is BankAccountCommand.WithdrawMoney -> handleWithdraw(command)
            is BankAccountCommand.WithdrawForTransfer -> handleWithdrawForTransfer(command)
            is BankAccountCommand.DepositFromTransfer -> handleDepositFromTransfer(command)
            is BankAccountCommand.FinishTransaction -> handleFinishTransaction(command)
            is BankAccountCommand.RollbackWithdrawForTransfer -> handleRollbackWithdrawForTransfer(command)
            is BankAccountCommand.RollbackDepositFromTransfer -> handleRollbackDepositFromTransfer(command)
            BankAccountCommand.NoOp -> null
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

    private suspend fun handleRollbackWithdrawForTransfer(command: BankAccountCommand.RollbackWithdrawForTransfer): BankAccount =
        lockProvider.withLock(command.aggregateId.value) {
            val account = findAccountOrThrow(command.aggregateId)
            val aggregate = BankAccountAggregate(account).rollbackWithdrawForTransfer(command)
            bankAccountRepository.upsert(aggregate)
        }

    private suspend fun handleRollbackDepositFromTransfer(command: BankAccountCommand.RollbackDepositFromTransfer): BankAccount =
        lockProvider.withLock(command.aggregateId.value) {
            val account = findAccountOrThrow(command.aggregateId)
            val aggregate = BankAccountAggregate(account).rollbackDepositFromTransfer(command)
            bankAccountRepository.upsert(aggregate)
        }

    private suspend fun findAccountOrThrow(aggregateId: mz.shared.domain.AggregateId): BankAccount =
        bankAccountRepository.findById(aggregateId)
            ?: throw IllegalArgumentException("BankAccount with id ${aggregateId.value} not found")

    /**
     * Handles BankAccountCommand asynchronously from the command channel.
     * Consumes commands from bankAccountCommandChannel with configurable polling.
     */
    @ServiceActivator(
        inputChannel = "bankAccountCommandChannel",
        requiresReply = "false",
        poller = Poller(fixedDelay = "\${adapters.command-channel.poller-delay-ms:100}"),
        async = "true",
    )
    suspend fun handleAsync(message: Message<BankAccountCommand>) {
        val command = message.payload
        logger.info("Processing command from channel: $command")
        handle(command)
        logger.info("Command processed successfully: $command")
    }
}
