package mz.bank.transaction.application.account

import mz.bank.transaction.application.transaction.BankTransactionRepository
import mz.bank.transaction.domain.BankTransactionCommand
import mz.shared.domain.AggregateId

/**
 * Extension functions to convert AccountEvent to BankTransactionCommand.
 * These mappers enable the event-driven saga choreography by translating
 * bank-account events into bank-transaction commands.
 *
 * Converts an AccountEvent to a BankTransactionCommand if applicable.
 * Returns null for events that don't require command processing.
 */
suspend fun AccountEvent.toCommand(repository: BankTransactionRepository): BankTransactionCommand? =
    when (this) {
        is AccountEvent.TransferWithdrawalStartedEvent -> toValidateWithdrawCommand()
        is AccountEvent.TransferDepositStartedEvent -> toValidateDepositCommand()
        is AccountEvent.TransactionFinishedEvent -> toFinishCommand(repository)
        is AccountEvent.AccountCreatedEvent,
        AccountEvent.DefaultAccountEvent,
        -> null
        // Rollback events can be handled here if needed
        is AccountEvent.TransferWithdrawalRolledBackEvent -> null
        is AccountEvent.TransferDepositRolledBackEvent -> null
    }

/**
 * Converts TransferWithdrawalStartedEvent to ValidateBankTransactionMoneyWithdraw command.
 * This validates that the withdrawal phase of the transfer has been completed.
 */
fun AccountEvent.TransferWithdrawalStartedEvent.toValidateWithdrawCommand() =
    BankTransactionCommand.ValidateBankTransactionMoneyWithdraw(
        aggregateId = AggregateId(transactionId),
        accountId = accountId,
        correlationId = transactionId,
    )

/**
 * Converts TransferDepositStartedEvent to ValidateBankTransactionMoneyDeposit command.
 * This validates that the deposit phase of the transfer has been completed.
 */
fun AccountEvent.TransferDepositStartedEvent.toValidateDepositCommand() =
    BankTransactionCommand.ValidateBankTransactionMoneyDeposit(
        aggregateId = AggregateId(transactionId),
        accountId = accountId,
        correlationId = transactionId,
    )

/**
 * Converts TransactionFinishedEvent to FinishBankTransaction command.
 * Queries the repository to retrieve the full transaction details needed for the command.
 *
 * Note: This approach handles the case where the event doesn't contain all required data.
 * The transaction is looked up to get fromAccountId and toAccountId.
 */
suspend fun AccountEvent.TransactionFinishedEvent.toFinishCommand(
    repository: BankTransactionRepository,
): BankTransactionCommand.FinishBankTransaction? {
    val transaction =
        repository.findById(AggregateId(transactionId))
            ?: return null

    return BankTransactionCommand.FinishBankTransaction(
        aggregateId = AggregateId(transactionId),
        correlationId = transaction.correlationId,
        fromAccountId = transaction.fromAccountId,
        toAccountId = transaction.toAccountId,
    )
}
