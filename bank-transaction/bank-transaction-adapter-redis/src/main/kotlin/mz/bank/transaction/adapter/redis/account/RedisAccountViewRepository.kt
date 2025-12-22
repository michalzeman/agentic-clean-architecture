package mz.bank.transaction.adapter.redis.account

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mz.bank.transaction.application.account.AccountViewRepository
import mz.bank.transaction.domain.account.AccountView
import mz.shared.domain.AggregateId
import org.springframework.stereotype.Component

/**
 * Redis implementation of AccountViewRepository.
 * Handles persistence operations for the account view read model using Spring Data Redis.
 * Uses indexed accountId field for efficient lookups.
 */
@Component
internal class RedisAccountViewRepository(
    private val repository: AccountViewDataRepository,
) : AccountViewRepository {
    override suspend fun findById(accountId: AggregateId): AccountView? =
        withContext(Dispatchers.IO) {
            repository.findByAccountId(accountId.value)?.toAccountView()
        }

    override suspend fun upsert(accountView: AccountView): AccountView =
        withContext(Dispatchers.IO) {
            // Find existing view to preserve technical ID
            val existing = repository.findByAccountId(accountView.accountId.value)
            val redisAccountView = accountView.toRedisAccountView(existing)
            repository.save(redisAccountView).toAccountView()
        }
}
