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
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * PostgreSQL implementation of BankAccountRepository using Spring Data JDBC.
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
internal class PostgresBankAccountRepository(
    private val repository: BankAccountJdbcRepository,
    private val transactionRefRepository: BankAccountTransactionRefJdbcRepository,
) : BankAccountRepository {
    override suspend fun findById(aggregateId: AggregateId): BankAccount? =
        withContext(Dispatchers.IO) {
            val account = repository.findByAggregateId(UUID.fromString(aggregateId.value))
            if (account == null) {
                null
            } else {
                val refs = transactionRefRepository.findAllByBankAccountId(account.id!!)
                account.toBankAccount(refs)
            }
        }

    @Transactional
    override suspend fun upsert(aggregate: BankAccountAggregate): BankAccount =
        withContext(Dispatchers.IO) {
            val existing = repository.findByAggregateId(UUID.fromString(aggregate.account.aggregateId.value))
            val entity =
                if (existing == null) {
                    // INSERT: version 0 → framework forces 1
                    aggregate.toPostgresBankAccount(existingId = null)
                } else {
                    // UPDATE: pass existing @Version token for optimistic lock guard
                    aggregate.toPostgresBankAccount(
                        existingId = existing.id,
                        existingVersion = existing.version,
                    )
                }
            val saved = repository.save(entity)
            val bankAccountId =
                saved.id
                    ?: throw IllegalStateException("Bank account id must not be null after save")

            transactionRefRepository.deleteAllByBankAccountId(bankAccountId)
            val refs = aggregate.toTransactionRefs(bankAccountId)
            if (refs.isNotEmpty()) {
                transactionRefRepository.saveAll(refs)
            }

            saved.toBankAccount(refs)
        }

    override suspend fun existsByEmail(email: Email): Boolean =
        withContext(Dispatchers.IO) {
            repository.existsByEmail(email.value)
        }
}
