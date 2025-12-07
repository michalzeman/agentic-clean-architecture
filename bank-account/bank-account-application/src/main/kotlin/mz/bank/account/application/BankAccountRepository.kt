package mz.bank.account.application

import mz.bank.account.domain.BankAccount
import mz.bank.account.domain.BankAccountAggregate
import mz.bank.account.domain.Email
import mz.shared.domain.AggregateId

/**
 * Repository interface for BankAccount persistence operations.
 * This is a port in hexagonal architecture, implemented by adapters.
 */
interface BankAccountRepository {
    /**
     * Finds a BankAccount by its aggregate ID.
     * @param aggregateId The unique identifier of the aggregate
     * @return The BankAccount if found, null otherwise
     */
    suspend fun findById(aggregateId: AggregateId): BankAccount?

    /**
     * Inserts or updates a BankAccount from the aggregate.
     * Domain events from the aggregate will be published after save.
     * @param aggregate The aggregate containing the account state and domain events
     * @return The persisted BankAccount
     */
    suspend fun upsert(aggregate: BankAccountAggregate): BankAccount

    /**
     * Checks if a BankAccount with the given email already exists.
     * @param email The email to check
     * @return true if an account with this email exists, false otherwise
     */
    suspend fun existsByEmail(email: Email): Boolean
}
