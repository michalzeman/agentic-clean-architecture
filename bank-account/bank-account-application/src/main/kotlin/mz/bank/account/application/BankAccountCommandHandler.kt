package mz.bank.account.application

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import mz.bank.account.domain.BankAccount
import mz.bank.account.domain.BankAccountAggregate
import mz.bank.account.domain.BankAccountCommand
import mz.bank.account.domain.BankAccountOperation
import mz.shared.connector.observability.ObservabilityMeters
import mz.shared.connector.observability.OperationOutcome
import mz.shared.domain.LockProvider
import org.slf4j.LoggerFactory
import org.springframework.integration.annotation.Poller
import org.springframework.integration.annotation.ServiceActivator
import org.springframework.messaging.Message
import org.springframework.stereotype.Component

private val logger = LoggerFactory.getLogger(BankAccountCommandHandler::class.java)

/**
 * Command handler for BankAccount aggregate.
 * Processes domain commands and persists aggregate state changes.
 */
@Component
class BankAccountCommandHandler(
    private val bankAccountRepository: BankAccountRepository,
    private val lockProvider: LockProvider,
    private val createBankAccountUseCase: CreateBankAccountUseCase,
    private val observabilityMeters: ObservabilityMeters,
) {
    private suspend fun <T> withOperationMeter(
        op: BankAccountOperation,
        block: suspend () -> T,
    ): T =
        try {
            val result = block()
            observabilityMeters.accountOperation(op.name.lowercase(), OperationOutcome.OK)
            result
        } catch (e: Exception) {
            observabilityMeters.accountOperation(op.name.lowercase(), OperationOutcome.FAIL)
            throw e
        }

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
            is BankAccountCommand.RollbackWithdrawForTransfer -> handleRollbackWithdrawForTransfer(command)
            is BankAccountCommand.RollbackDepositFromTransfer -> handleRollbackDepositFromTransfer(command)
            is BankAccountCommand.FinishTransaction -> handleFinishTransaction(command)
            is BankAccountCommand.FinishTransactions -> handleFinishTransactions(command)
            BankAccountCommand.NoOp -> null
        }

    private suspend fun handleCreate(command: BankAccountCommand.CreateAccount): BankAccount =
        withOperationMeter(BankAccountOperation.CREATE) {
            lockProvider.withLock(command.email.value) {
                createBankAccountUseCase.execute(command)
            }
        }

    private suspend fun handleDeposit(command: BankAccountCommand.DepositMoney): BankAccount =
        withOperationMeter(BankAccountOperation.DEPOSIT) {
            lockProvider.withLock(command.aggregateId.value) {
                val account = findAccountOrThrow(command.aggregateId)
                val aggregate = BankAccountAggregate(account).deposit(command)
                bankAccountRepository.upsert(aggregate)
            }
        }

    private suspend fun handleWithdraw(command: BankAccountCommand.WithdrawMoney): BankAccount =
        withOperationMeter(BankAccountOperation.WITHDRAW) {
            lockProvider.withLock(command.aggregateId.value) {
                val account = findAccountOrThrow(command.aggregateId)
                val aggregate = BankAccountAggregate(account).withdraw(command)
                bankAccountRepository.upsert(aggregate)
            }
        }

    private suspend fun handleWithdrawForTransfer(command: BankAccountCommand.WithdrawForTransfer): BankAccount =
        withOperationMeter(BankAccountOperation.TRANSFER_WITHDRAW) {
            lockProvider.withLock(command.aggregateId.value) {
                val account = findAccountOrThrow(command.aggregateId)
                val aggregate = BankAccountAggregate(account).withdrawForTransfer(command)
                bankAccountRepository.upsert(aggregate)
            }
        }

    private suspend fun handleDepositFromTransfer(command: BankAccountCommand.DepositFromTransfer): BankAccount =
        withOperationMeter(BankAccountOperation.TRANSFER_DEPOSIT) {
            lockProvider.withLock(command.aggregateId.value) {
                val account = findAccountOrThrow(command.aggregateId)
                val aggregate = BankAccountAggregate(account).depositFromTransfer(command)
                bankAccountRepository.upsert(aggregate)
            }
        }

    private suspend fun handleFinishTransactions(command: BankAccountCommand.FinishTransactions): BankAccount =
        supervisorScope {
            command.commands
                .map {
                    async {
                        handleFinishTransaction(it)
                    }
                }.awaitAll()
                .first()
        }

    private suspend fun handleFinishTransaction(command: BankAccountCommand.FinishTransaction): BankAccount =
        withOperationMeter(BankAccountOperation.FINISH) {
            lockProvider.withLock(command.aggregateId.value) {
                val account = findAccountOrThrow(command.aggregateId)
                if (command.transactionId in account.openedTransactions) {
                    val aggregate = BankAccountAggregate(account).finishTransaction(command)
                    bankAccountRepository.upsert(aggregate)
                } else {
                    account
                }
            }
        }

    private suspend fun handleRollbackWithdrawForTransfer(command: BankAccountCommand.RollbackWithdrawForTransfer): BankAccount =
        withOperationMeter(BankAccountOperation.ROLLBACK_WITHDRAW) {
            lockProvider.withLock(command.aggregateId.value) {
                val account = findAccountOrThrow(command.aggregateId)
                val aggregate = BankAccountAggregate(account).rollbackWithdrawForTransfer(command)
                bankAccountRepository.upsert(aggregate)
            }
        }

    private suspend fun handleRollbackDepositFromTransfer(command: BankAccountCommand.RollbackDepositFromTransfer): BankAccount =
        withOperationMeter(BankAccountOperation.ROLLBACK_DEPOSIT) {
            lockProvider.withLock(command.aggregateId.value) {
                val account = findAccountOrThrow(command.aggregateId)
                val aggregate = BankAccountAggregate(account).rollbackDepositFromTransfer(command)
                bankAccountRepository.upsert(aggregate)
            }
        }

    private suspend fun findAccountOrThrow(aggregateId: mz.shared.domain.AggregateId): BankAccount =
        bankAccountRepository.findById(aggregateId)
            ?: throw IllegalArgumentException("BankAccount with id ${aggregateId.value} not found")

    /**
     * Handles BankAccountCommand asynchronously from the command channel.
     * Consumes commands from bankAccountCommandChannel with configurable polling.
     * Uses the shared redisStreamTaskExecutor for thread pool management.
     */
    @ServiceActivator(
        inputChannel = "bankAccountCommandChannel",
        requiresReply = "false",
        poller =
            Poller(
                fixedDelay = "\${app.integration.redis.poller.fixed-delay:100}",
                maxMessagesPerPoll = "\${app.integration.redis.poller.max-messages-per-poll:10}",
                taskExecutor = "redisStreamTaskExecutor",
            ),
        async = "true",
    )
    suspend fun handleAsync(message: Message<BankAccountCommand>) {
        val command = message.payload
        logger.info("Processing command from channel: $command")
        handle(command)
        logger.info("Command processed successfully: $command")
    }
}
