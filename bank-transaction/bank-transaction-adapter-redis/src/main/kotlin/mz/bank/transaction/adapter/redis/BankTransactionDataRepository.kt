package mz.bank.transaction.adapter.redis

import org.springframework.data.repository.CrudRepository
import java.util.UUID

/**
 * Spring Data Redis repository for BankTransaction persistence.
 * Provides CRUD operations for RedisBankTransaction entities.
 */
internal interface BankTransactionDataRepository : CrudRepository<RedisBankTransaction, UUID>
