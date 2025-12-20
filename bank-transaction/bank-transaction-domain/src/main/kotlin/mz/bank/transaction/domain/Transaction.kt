package mz.bank.transaction.domain

import mz.shared.domain.AggregateId
import java.math.BigDecimal
import java.time.Instant

/**
 * Transaction is the core domain entity representing a money transfer transaction.
 * It orchestrates the saga pattern for transferring money between two accounts.
 */
data class Transaction(
    val aggregateId: AggregateId,
    val correlationId: String,
    val fromAccountId: AggregateId,
    val toAccountId: AggregateId,
    val amount: BigDecimal,
    val moneyWithdrawn: Boolean = false,
    val moneyDeposited: Boolean = false,
    val status: TransactionStatus = TransactionStatus.INITIALIZED,
    val version: Long = 0L,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
) {
    init {
        require(amount > BigDecimal.ZERO) { "Transaction amount must be positive" }
        require(fromAccountId != toAccountId) { "Source and destination accounts must be different" }
        require(correlationId.isNotBlank()) { "Correlation ID cannot be blank" }

        // Status consistency checks
        when (status) {
            TransactionStatus.INITIALIZED -> {
                require(!moneyWithdrawn && !moneyDeposited) {
                    "INITIALIZED status requires no operations completed"
                }
            }
            TransactionStatus.CREATED -> {
                // In progress, any combination of flags is valid
            }
            TransactionStatus.FINISHED -> {
                require(moneyWithdrawn && moneyDeposited) {
                    "FINISHED status requires both withdraw and deposit completed"
                }
            }
            TransactionStatus.FAILED -> {
                // Failed state, flags can be in any state
            }
        }
    }
}
