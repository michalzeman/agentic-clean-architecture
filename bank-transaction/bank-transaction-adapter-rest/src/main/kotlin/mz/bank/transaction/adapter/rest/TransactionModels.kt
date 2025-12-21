package mz.bank.transaction.adapter.rest

import mz.bank.transaction.domain.Transaction
import mz.bank.transaction.domain.TransactionStatus
import java.math.BigDecimal
import java.time.Instant

/**
 * Request to create a new transaction.
 */
data class CreateTransactionRequest(
    val fromAccountId: String,
    val toAccountId: String,
    val amount: BigDecimal,
)

/**
 * Request to validate money withdrawal.
 */
data class ValidateWithdrawRequest(
    val transactionId: String,
)

/**
 * Request to validate money deposit.
 */
data class ValidateDepositRequest(
    val transactionId: String,
)

/**
 * Request to finish a transaction.
 */
data class FinishTransactionRequest(
    val fromAccountId: String,
    val toAccountId: String,
)

/**
 * Request to cancel a transaction.
 */
data class CancelTransactionRequest(
    val fromAccountId: String,
    val toAccountId: String,
    val amount: BigDecimal,
)

/**
 * Response containing transaction details.
 */
data class TransactionResponse(
    val transactionId: String,
    val correlationId: String,
    val fromAccountId: String,
    val toAccountId: String,
    val amount: BigDecimal,
    val moneyWithdrawn: Boolean,
    val moneyDeposited: Boolean,
    val status: TransactionStatus,
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
 * Extension function to convert Transaction domain entity to API response.
 */
fun Transaction.toResponse(): TransactionResponse =
    TransactionResponse(
        transactionId = aggregateId.value,
        correlationId = correlationId,
        fromAccountId = fromAccountId.value,
        toAccountId = toAccountId.value,
        amount = amount,
        moneyWithdrawn = moneyWithdrawn,
        moneyDeposited = moneyDeposited,
        status = status,
        version = version,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
