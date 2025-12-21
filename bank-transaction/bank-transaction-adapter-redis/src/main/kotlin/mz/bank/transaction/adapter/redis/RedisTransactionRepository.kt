package mz.bank.transaction.adapter.redis

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mz.bank.transaction.application.TransactionRepository
import mz.bank.transaction.domain.Transaction
import mz.bank.transaction.domain.TransactionAggregate
import mz.shared.domain.AggregateId
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Redis implementation of TransactionRepository.
 * Handles persistence operations using Spring Data Redis.
 */
@Component
internal class RedisTransactionRepository(
    private val repository: TransactionDataRepository,
) : TransactionRepository {
    override suspend fun findById(aggregateId: AggregateId): Transaction? =
        withContext(Dispatchers.IO) {
            repository
                .findById(UUID.fromString(aggregateId.value))
                .map { it.toTransaction() }
                .orElse(null)
        }

    override suspend fun upsert(aggregate: TransactionAggregate): Transaction =
        withContext(Dispatchers.IO) {
            val redisTransaction = aggregate.toRedisTransaction()
            repository.save(redisTransaction).toTransaction()
        }
}
