package mz.bank.account.adapter.redis

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
 * Redis implementation of BankAccountRepository.
 * Handles persistence operations using Spring Data Redis.
 * Active when the 'redis-persistence' Spring profile is enabled.
 */
@Component
@Profile("redis-persistence")
internal class RedisBankAccountRepository(
    private val repository: BankAccountDataRepository,
) : BankAccountRepository {
    override suspend fun findById(aggregateId: AggregateId): BankAccount? =
        withContext(Dispatchers.IO) {
            repository
                .findById(UUID.fromString(aggregateId.value))
                .map { it.toBankAccount() }
                .orElse(null)
        }

    override suspend fun upsert(aggregate: BankAccountAggregate): BankAccount =
        withContext(Dispatchers.IO) {
            val redisBankAccount = aggregate.toRedisBankAccount()
            repository.save(redisBankAccount).toBankAccount()
        }

    override suspend fun existsByEmail(email: Email): Boolean =
        withContext(Dispatchers.IO) {
            repository.existsByEmail(email.value)
        }
}
