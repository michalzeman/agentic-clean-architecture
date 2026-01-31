package mz.bank.transaction.application.transaction

import mz.bank.transaction.domain.BankTransaction
import mz.bank.transaction.domain.BankTransactionAggregate
import mz.bank.transaction.domain.BankTransactionCommand
import mz.shared.domain.AggregateId
import mz.shared.domain.LockProvider
import org.slf4j.LoggerFactory
import org.springframework.integration.annotation.Poller
import org.springframework.integration.annotation.ServiceActivator
import org.springframework.messaging.Message
import org.springframework.stereotype.Component

/**
 * Command handler for BankTransaction aggregate.
 * Processes domain commands and persists aggregate state changes.
 */
@Component
class BankTransactionCommandHandler(
    private val bankTransactionRepository: BankTransactionRepository,
    private val lockProvider: LockProvider,
    private val createTransactionUseCase: CreateTransactionUseCase,
) {
    private val logger = LoggerFactory.getLogger(BankTransactionCommandHandler::class.java)

    /**
     * Handles a BankTransactionCommand and returns the updated BankTransaction.
     * Uses distributed locking to ensure consistency.
     */
    suspend fun handle(command: BankTransactionCommand): BankTransaction {
        logger.info("Handling command: {}", command::class.simpleName)
        logger.debug("Command details: {}", command)
        return when (command) {
            is BankTransactionCommand.CreateBankTransaction -> handleCreate(command)
            is BankTransactionCommand.ValidateBankTransactionMoneyWithdraw -> handleValidateWithdraw(command)
            is BankTransactionCommand.ValidateBankTransactionMoneyDeposit -> handleValidateDeposit(command)
            is BankTransactionCommand.FinishBankTransaction -> handleFinish(command)
            is BankTransactionCommand.CancelBankTransaction -> handleCancel(command)
        }
    }

    /**
     * Handles BankTransactionCommand asynchronously from the command channel.
     * Consumes commands from bankTransactionCommandChannel with configurable polling.
     * Uses the shared redisStreamTaskExecutor for thread pool management.
     */
    @ServiceActivator(
        inputChannel = "bankTransactionCommandChannel",
        requiresReply = "false",
        poller =
            Poller(
                fixedDelay = "\${app.integration.redis.poller.fixed-delay:100}",
                maxMessagesPerPoll = "\${app.integration.redis.poller.max-messages-per-poll:10}",
                taskExecutor = "redisStreamTaskExecutor",
            ),
        async = "true",
    )
    suspend fun handleAsync(message: Message<BankTransactionCommand>) {
        val command = message.payload
        logger.info("Processing command from channel: $command")
        handle(command)
        logger.info("Command processed successfully: $command")
    }

    private suspend fun handleCreate(command: BankTransactionCommand.CreateBankTransaction): BankTransaction {
        logger.info("Creating new transaction with correlationId: {}", command.correlationId)
        val result = createTransactionUseCase(command)
        logger.info("Transaction created with id: {}", result.aggregateId.value)
        return result
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

    private suspend fun findBankTransactionOrThrow(aggregateId: AggregateId): BankTransaction =
        bankTransactionRepository.findById(aggregateId)
            ?: throw IllegalArgumentException("BankTransaction with id ${aggregateId.value} not found")
}
