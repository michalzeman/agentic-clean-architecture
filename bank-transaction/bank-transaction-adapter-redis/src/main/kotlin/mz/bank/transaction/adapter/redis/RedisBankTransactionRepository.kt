package mz.bank.transaction.adapter.redis

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mz.bank.transaction.application.transaction.BankTransactionRepository
import mz.bank.transaction.domain.BankTransaction
import mz.bank.transaction.domain.BankTransactionAggregate
import mz.shared.domain.AggregateId
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Redis implementation of BankTransactionRepository.
 * Handles persistence operations using Spring Data Redis.
 * Active when the 'redis-persistence' Spring profile is enabled.
 */
@Component
@Profile("redis-persistence")
internal class RedisBankTransactionRepository(
    private val repository: BankTransactionDataRepository,
) : BankTransactionRepository {
    override suspend fun findById(aggregateId: AggregateId): BankTransaction? =
        withContext(Dispatchers.IO) {
            repository
                .findById(UUID.fromString(aggregateId.value))
                .map { it.toBankTransaction() }
                .orElse(null)
        }

    override suspend fun upsert(aggregate: BankTransactionAggregate): BankTransaction =
        withContext(Dispatchers.IO) {
            val redisBankTransaction = aggregate.toRedisBankTransaction()
            repository.save(redisBankTransaction).toBankTransaction()
        }
}
