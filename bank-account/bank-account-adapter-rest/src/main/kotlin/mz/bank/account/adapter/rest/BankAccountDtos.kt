package mz.bank.account.adapter.rest

import mz.bank.account.domain.BankAccount
import java.math.BigDecimal
import java.time.Instant

/**
 * Request to create a new bank account.
 */
data class CreateAccountRequest(
    val email: String,
    val initialBalance: BigDecimal,
)

/**
 * Request to deposit money into an account.
 */
data class DepositMoneyRequest(
    val amount: BigDecimal,
)

/**
 * Request to withdraw money from an account.
 */
data class WithdrawMoneyRequest(
    val amount: BigDecimal,
)

/**
 * Request to withdraw money for a transfer transaction.
 */
data class TransferWithdrawRequest(
    val transactionId: String,
    val amount: BigDecimal,
)

/**
 * Request to deposit money from a transfer transaction.
 */
data class TransferDepositRequest(
    val transactionId: String,
    val amount: BigDecimal,
)

/**
 * Request to finish a transaction.
 */
data class FinishTransactionRequest(
    val transactionId: String,
)

/**
 * Response containing bank account details.
 */
data class BankAccountResponse(
    val accountId: String,
    val email: String,
    val balance: BigDecimal,
    val openedTransactions: Set<String>,
    val finishedTransactions: Set<String>,
    val version: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/**
 * Error response for API errors.
 */
data class ErrorResponse(
    val error: String,
    val message: String,
    val timestamp: Instant = Instant.now(),
)

/**
 * Extension function to convert BankAccount domain entity to API response.
 */
fun BankAccount.toResponse(): BankAccountResponse =
    BankAccountResponse(
        accountId = aggregateId.value,
        email = email.value,
        balance = amount,
        openedTransactions = openedTransactions,
        finishedTransactions = finishedTransactions,
        version = version,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
