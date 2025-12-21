package mz.bank.transaction.application

import mz.bank.transaction.domain.BankTransaction
import mz.bank.transaction.domain.BankTransactionAggregate
import mz.shared.domain.AggregateId

/**
 * Repository interface for BankTransaction persistence operations.
 * Abstracts persistence details from the domain layer.
 */
interface BankTransactionRepository {
    /**
     * Finds a BankTransaction by its aggregate ID.
     *
     * @return The BankTransaction if found, null otherwise
     */
    suspend fun findById(aggregateId: AggregateId): BankTransaction?

    /**
     * Inserts or updates a BankTransaction from the aggregate.
     * Publishes domain events after persistence.
     *
     * @return The persisted BankTransaction
     */
    suspend fun upsert(aggregate: BankTransactionAggregate): BankTransaction
}
