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
 *   The domain pre-increments version in applyEvent (version = N+1), but Spring Data
 *   JDBC's @Version expects the current DB version (N) in the entity so it can generate
 *   WHERE version = N and then set version = N+1 in the DB. We therefore pass
 *   versionOverride = bankTransaction.version - 1 into the entity before calling save().
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
                    // INSERT: version stays as-is (0 for a new record)
                    aggregate.toPostgresBankTransaction(existingId = null)
                } else {
                    // UPDATE: pass version - 1 so @Version sees the current DB version
                    aggregate.toPostgresBankTransaction(
                        existingId = existing.id,
                        versionOverride = aggregate.bankTransaction.version - 1,
                    )
                }
            repository.save(entity).toBankTransaction()
        }
}
