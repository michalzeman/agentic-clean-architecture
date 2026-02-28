package mz.bank.account.adapter.postgresql

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mz.bank.account.application.BankAccountRepository
import mz.bank.account.domain.BankAccount
import mz.bank.account.domain.BankAccountAggregate
import mz.bank.account.domain.Email
import mz.shared.domain.AggregateId
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * PostgreSQL implementation of BankAccountRepository using Spring Data JDBC.
 *
 * INSERT path: aggregate_id not yet in DB → save() issues INSERT.
 * UPDATE path: aggregate_id already exists → save() issues UPDATE.
 *   The domain pre-increments version in applyEvent (version = N+1), but Spring Data
 *   JDBC's @Version expects the current DB version (N) in the entity so it can generate
 *   WHERE version = N and then set version = N+1 in the DB. We therefore pass
 *   versionOverride = account.version - 1 into the entity before calling save().
 *
 * In both paths @DomainEvents / @AfterDomainEventPublication fire automatically after save().
 *
 * Active when the 'postgres-persistence' Spring profile is enabled.
 */
@Component
@Profile("postgres-persistence")
internal class PostgresBankAccountRepository(
    private val repository: BankAccountJdbcRepository,
) : BankAccountRepository {
    override suspend fun findById(aggregateId: AggregateId): BankAccount? =
        withContext(Dispatchers.IO) {
            repository
                .findByAggregateId(UUID.fromString(aggregateId.value))
                ?.toBankAccount()
        }

    override suspend fun upsert(aggregate: BankAccountAggregate): BankAccount =
        withContext(Dispatchers.IO) {
            val existing = repository.findByAggregateId(UUID.fromString(aggregate.account.aggregateId.value))
            val entity =
                if (existing == null) {
                    // INSERT: version stays as-is (0 for a new record)
                    aggregate.toPostgresBankAccount(existingId = null)
                } else {
                    // UPDATE: pass version - 1 so @Version sees the current DB version
                    aggregate.toPostgresBankAccount(
                        existingId = existing.id,
                        versionOverride = aggregate.account.version - 1,
                    )
                }
            repository.save(entity).toBankAccount()
        }

    override suspend fun existsByEmail(email: Email): Boolean =
        withContext(Dispatchers.IO) {
            repository.existsByEmail(email.value)
        }
}
