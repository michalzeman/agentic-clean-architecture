package mz.bank.transaction.adapter.redis.account

import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

/**
 * Spring Data Redis repository for AccountView persistence.
 * Uses indexed accountId field for efficient business key lookups.
 */
@Repository
internal interface AccountViewDataRepository : CrudRepository<RedisAccountView, String> {
    /**
     * Find account view by business accountId (indexed field).
     */
    fun findByAccountId(accountId: String): RedisAccountView?
}
