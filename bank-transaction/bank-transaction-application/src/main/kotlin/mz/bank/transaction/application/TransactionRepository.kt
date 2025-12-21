package mz.bank.transaction.application

import mz.bank.transaction.domain.Transaction
import mz.bank.transaction.domain.TransactionAggregate
import mz.shared.domain.AggregateId

/**
 * Repository interface for Transaction persistence operations.
 * This is a port in hexagonal architecture, implemented by adapters.
 */
interface TransactionRepository {
    /**
     * Finds a Transaction by its aggregate ID.
     * @param aggregateId The unique identifier of the aggregate
     * @return The Transaction if found, null otherwise
     */
    suspend fun findById(aggregateId: AggregateId): Transaction?

    /**
     * Inserts or updates a Transaction from the aggregate.
     * Domain events from the aggregate will be published after save.
     * @param aggregate The aggregate containing the transaction state and domain events
     * @return The persisted Transaction
     */
    suspend fun upsert(aggregate: TransactionAggregate): Transaction
}
