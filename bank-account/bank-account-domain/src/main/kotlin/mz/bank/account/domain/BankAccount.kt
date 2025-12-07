package mz.bank.account.domain

import mz.shared.domain.AggregateId
import java.math.BigDecimal
import java.time.Instant

/**
 * BankAccount is the core domain entity representing a bank account.
 * It maintains the account state including balance and open transactions.
 */
data class BankAccount(
    val aggregateId: AggregateId,
    val email: Email,
    val amount: BigDecimal,
    val openedTransactions: Set<String> = emptySet(),
    val finishedTransactions: Set<String> = emptySet(),
    val version: Long = 0L,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
) {
    init {
        require(amount >= BigDecimal.ZERO) { "Account balance cannot be negative" }
        require(openedTransactions.intersect(finishedTransactions).isEmpty()) {
            "Transaction IDs cannot exist in both opened and finished sets"
        }
    }
}
