package mz.bank.account.adapter.redis

import org.springframework.data.repository.CrudRepository
import java.util.UUID

/**
 * Spring Data Redis repository for BankAccount persistence.
 * Provides CRUD operations for RedisBankAccount entities.
 */
internal interface BankAccountDataRepository : CrudRepository<RedisBankAccount, UUID>
