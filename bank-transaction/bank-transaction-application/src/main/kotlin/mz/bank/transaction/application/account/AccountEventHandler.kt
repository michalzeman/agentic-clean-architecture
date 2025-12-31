package mz.bank.transaction.application.account

import mz.bank.transaction.application.transaction.BankTransactionCommandGateway
import mz.bank.transaction.application.transaction.BankTransactionRepository
import mz.bank.transaction.domain.account.AccountView
import org.slf4j.LoggerFactory
import org.springframework.integration.annotation.ServiceActivator
import org.springframework.stereotype.Component

private val logger = LoggerFactory.getLogger(AccountEventHandler::class.java)

/**
 * Handler for AccountEvent from the bank-account service.
 *
 * Responsibilities:
 * 1. Maintains a local account view (read model) for account creation events
 * 2. Orchestrates the saga by converting account events to bank transaction commands
 * 3. Sends commands to the bank transaction aggregate via the command gateway
 *
 * This handler enables event-driven choreography for the money transfer saga.
 */
@Component
class AccountEventHandler(
    private val accountViewRepository: AccountViewRepository,
    private val bankTransactionCommandGateway: BankTransactionCommandGateway,
    private val bankTransactionRepository: BankTransactionRepository,
) {
    /**
     * Handles incoming AccountEvent and routes to appropriate handler based on event type.
     */
    @ServiceActivator(inputChannel = "inboundBankAccountEventsChannel")
    suspend fun handle(event: AccountEvent) {
        logger.info("Handling AccountEvent: ${event::class.simpleName}")

        when (event) {
            is AccountEvent.AccountCreatedEvent -> handleAccountCreated(event)
            is AccountEvent.TransferWithdrawalStartedEvent -> handleTransferWithdrawalStarted(event)
            is AccountEvent.TransferDepositStartedEvent -> handleTransferDepositStarted(event)
            is AccountEvent.TransactionFinishedEvent -> handleTransactionFinished(event)
            is AccountEvent.TransferWithdrawalRolledBackEvent -> handleTransferWithdrawalRolledBack(event)
            is AccountEvent.TransferDepositRolledBackEvent -> handleTransferDepositRolledBack(event)
            AccountEvent.DefaultAccountEvent -> logger.debug("Ignoring DefaultAccountEvent")
        }
    }

    /**
     * Handles AccountCreatedEvent by storing the account ID in the local view.
     */
    private suspend fun handleAccountCreated(event: AccountEvent.AccountCreatedEvent) {
        logger.info("Handling AccountCreatedEvent for accountId=${event.accountId}")

        val accountView = AccountView(accountId = event.accountId)
        accountViewRepository.upsert(accountView)

        logger.info("AccountView created for accountId=${event.accountId}")
    }

    /**
     * Handles TransferWithdrawalStartedEvent by sending ValidateWithdraw command.
     * This advances the saga after the withdrawal phase completes.
     */
    private suspend fun handleTransferWithdrawalStarted(event: AccountEvent.TransferWithdrawalStartedEvent) {
        logger.info("Transfer withdrawal started for transactionId=${event.transactionId}, accountId=${event.accountId}")

        val command = event.toValidateWithdrawCommand()
        bankTransactionCommandGateway.send(command)

        logger.info("Sent ValidateWithdraw command for transactionId=${event.transactionId}")
    }

    /**
     * Handles TransferDepositStartedEvent by sending ValidateDeposit command.
     * This advances the saga after the deposit phase completes.
     */
    private suspend fun handleTransferDepositStarted(event: AccountEvent.TransferDepositStartedEvent) {
        logger.info("Transfer deposit started for transactionId=${event.transactionId}, accountId=${event.accountId}")

        val command = event.toValidateDepositCommand()
        bankTransactionCommandGateway.send(command)

        logger.info("Sent ValidateDeposit command for transactionId=${event.transactionId}")
    }

    /**
     * Handles TransactionFinishedEvent by sending FinishBankTransaction command.
     * This completes the saga after both accounts have finished the transaction.
     *
     * Note: This event may be received from both source and destination accounts.
     * The FinishBankTransaction command should be idempotent to handle duplicate calls.
     */
    private suspend fun handleTransactionFinished(event: AccountEvent.TransactionFinishedEvent) {
        logger.info("Transaction finished for transactionId=${event.transactionId}, accountId=${event.accountId}")

        val command = event.toFinishCommand(bankTransactionRepository)
        if (command != null) {
            bankTransactionCommandGateway.send(command)
            logger.info("Sent FinishBankTransaction command for transactionId=${event.transactionId}")
        } else {
            logger.warn("Could not create FinishBankTransaction command - transaction not found: ${event.transactionId}")
        }
    }

    /**
     * Handles TransferWithdrawalRolledBackEvent.
     * Currently logs the event. Can be extended to trigger compensating actions.
     */
    private fun handleTransferWithdrawalRolledBack(event: AccountEvent.TransferWithdrawalRolledBackEvent) {
        logger.info("Transfer withdrawal rolled back for transactionId=${event.transactionId}, accountId=${event.accountId}")
        // TODO: Implement rollback compensation if needed
    }

    /**
     * Handles TransferDepositRolledBackEvent.
     * Currently logs the event. Can be extended to trigger compensating actions.
     */
    private fun handleTransferDepositRolledBack(event: AccountEvent.TransferDepositRolledBackEvent) {
        logger.info("Transfer deposit rolled back for transactionId=${event.transactionId}, accountId=${event.accountId}")
        // TODO: Implement rollback compensation if needed
    }
}
