package mz.bank.transaction.adapter.redis

import org.springframework.data.repository.CrudRepository
import java.util.UUID

/**
 * Spring Data Redis repository for Transaction persistence.
 * Provides CRUD operations for RedisTransaction entities.
 */
internal interface TransactionDataRepository : CrudRepository<RedisTransaction, UUID>
