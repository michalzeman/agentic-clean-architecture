package mz.bank.account.adapter.redis

import org.springframework.data.repository.CrudRepository
import java.util.UUID

/**
 * Spring Data Redis repository for BankAccount persistence.
 * Provides CRUD operations for RedisBankAccount entities.
 */
internal interface BankAccountDataRepository : CrudRepository<RedisBankAccount, UUID> {
    /**
     * Checks if a BankAccount with the given email exists.
     * @param email The email to check
     * @return true if an account with this email exists, false otherwise
     */
    fun existsByEmail(email: String): Boolean
}
