package mz.bank.transaction.domain

import mz.shared.domain.AggregateId
import java.math.BigDecimal
import java.time.Instant

/**
 * BankTransaction is the core domain entity representing a money transfer transaction.
 * It orchestrates the saga pattern for transferring money between two accounts.
 */
data class BankTransaction(
    val aggregateId: AggregateId,
    val correlationId: String,
    val fromAccountId: AggregateId,
    val toAccountId: AggregateId,
    val amount: BigDecimal,
    val moneyWithdrawn: Boolean = false,
    val moneyDeposited: Boolean = false,
    val status: BankTransactionStatus = BankTransactionStatus.INITIALIZED,
    val version: Long = 0L,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
) {
    init {
        require(amount > BigDecimal.ZERO) { "BankTransaction amount must be positive" }
        require(fromAccountId != toAccountId) { "Source and destination accounts must be different" }
        require(correlationId.isNotBlank()) { "Correlation ID cannot be blank" }

        // Status consistency checks
        when (status) {
            BankTransactionStatus.INITIALIZED -> {
                require(!moneyWithdrawn && !moneyDeposited) {
                    "INITIALIZED status requires no operations completed"
                }
            }
            BankTransactionStatus.CREATED -> {
                // In progress, any combination of flags is valid
            }
            BankTransactionStatus.FINISHED -> {
                require(moneyWithdrawn && moneyDeposited) {
                    "FINISHED status requires both withdraw and deposit completed"
                }
            }
            BankTransactionStatus.FAILED -> {
                // Failed state, flags can be in any state
            }
        }
    }
}
