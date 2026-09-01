package mz.bank.transaction.adapter.postgresql

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
 * PostgreSQL implementation of BankTransactionRepository using Spring Data JDBC.
 *
 * INSERT path: aggregate_id not yet in DB → save() issues INSERT.
 * UPDATE path: aggregate_id already exists → save() issues UPDATE.
 *   The domain version (event count) is written as-is into the `aggregate_version` column.
 *   The `version` column (@Version token) carries the loaded row's value so Spring Data JDBC's
 *   @Version mechanism guards against concurrent modifications.
 *
 * In both paths @DomainEvents / @AfterDomainEventPublication fire automatically after save().
 *
 * Active when the 'postgres-persistence' Spring profile is enabled.
 */
@Component
@Profile("postgres-persistence")
internal class PostgresBankTransactionRepository(
    private val repository: BankTransactionJdbcRepository,
) : BankTransactionRepository {
    override suspend fun findById(aggregateId: AggregateId): BankTransaction? =
        withContext(Dispatchers.IO) {
            repository
                .findByAggregateId(UUID.fromString(aggregateId.value))
                ?.toBankTransaction()
        }

    override suspend fun upsert(aggregate: BankTransactionAggregate): BankTransaction =
        withContext(Dispatchers.IO) {
            val existing = repository.findByAggregateId(UUID.fromString(aggregate.bankTransaction.aggregateId.value))
            val entity =
                if (existing == null) {
                    // INSERT: version 0 → framework forces 1
                    aggregate.toPostgresBankTransaction(existingId = null)
                } else {
                    // UPDATE: pass existing @Version token for optimistic lock guard
                    aggregate.toPostgresBankTransaction(
                        existingId = existing.id,
                        existingVersion = existing.version,
                    )
                }
            repository.save(entity).toBankTransaction()
        }
}
