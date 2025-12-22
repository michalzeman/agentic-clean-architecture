package mz.bank.transaction.adapter.redis.account

import mz.bank.transaction.domain.account.AccountView
import mz.shared.domain.AggregateId
import org.springframework.data.annotation.Id
import org.springframework.data.redis.core.RedisHash
import org.springframework.data.redis.core.index.Indexed
import java.util.UUID

/**
 * Redis persistence entity for AccountView.
 * Maps the domain entity to Redis storage using Spring Data Redis.
 * This is a simple read model without domain events.
 *
 * Uses a technical ID for Redis internal identification and an indexed accountId for business queries.
 */
@RedisHash("AccountView")
internal class RedisAccountView(
    @field:Id val id: String = UUID.randomUUID().toString(),
    @field:Indexed val accountId: String,
)

/**
 * Converts RedisAccountView persistence entity to AccountView domain entity.
 */
internal fun RedisAccountView.toAccountView(): AccountView =
    AccountView(
        accountId = AggregateId(accountId),
    )

/**
 * Converts AccountView domain entity to RedisAccountView persistence entity.
 * If an existing RedisAccountView is provided, preserves its technical ID.
 */
internal fun AccountView.toRedisAccountView(existing: RedisAccountView? = null): RedisAccountView =
    RedisAccountView(
        id = existing?.id ?: UUID.randomUUID().toString(),
        accountId = accountId.value,
    )
