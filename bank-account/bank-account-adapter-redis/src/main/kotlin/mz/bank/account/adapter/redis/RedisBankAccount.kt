package mz.bank.account.adapter.redis

import mz.bank.account.domain.BankAccount
import mz.bank.account.domain.BankAccountAggregate
import mz.bank.account.domain.BankAccountEvent
import mz.bank.account.domain.Email
import mz.shared.domain.AggregateId
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Transient
import org.springframework.data.annotation.Version
import org.springframework.data.domain.AfterDomainEventPublication
import org.springframework.data.domain.DomainEvents
import org.springframework.data.redis.core.RedisHash
import org.springframework.data.redis.core.index.Indexed
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Redis persistence entity for BankAccount.
 * Maps the domain entity to Redis storage using Spring Data Redis.
 */
@RedisHash("BankAccount")
internal open class RedisBankAccount(
    @field:Id val id: UUID,
    @field:Indexed val email: String,
    val amount: BigDecimal,
    val openedTransactions: Set<String> = emptySet(),
    val finishedTransactions: Set<String> = emptySet(),
    @field:Version val version: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    @Transient
    var domainEvents: MutableList<BankAccountEvent> = mutableListOf()

    @DomainEvents
    open fun domainEvents(): Collection<BankAccountEvent> = domainEvents.toList()

    @AfterDomainEventPublication
    open fun clearDomainEvents() {
        domainEvents.clear()
    }
}

/**
 * Converts RedisBankAccount persistence entity to BankAccount domain entity.
 */
internal fun RedisBankAccount.toBankAccount(): BankAccount =
    BankAccount(
        aggregateId = AggregateId(id.toString()),
        email = Email(email),
        amount = amount,
        openedTransactions = openedTransactions,
        finishedTransactions = finishedTransactions,
        version = version,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

/**
 * Converts BankAccountAggregate to RedisBankAccount persistence entity.
 * Includes domain events for publication after save.
 */
internal fun BankAccountAggregate.toRedisBankAccount(): RedisBankAccount {
    val redisBankAccount =
        RedisBankAccount(
            id = UUID.fromString(account.aggregateId.value),
            email = account.email.value,
            amount = account.amount,
            openedTransactions = account.openedTransactions,
            finishedTransactions = account.finishedTransactions,
            version = account.version,
            createdAt = account.createdAt,
            updatedAt = account.updatedAt,
        )
    redisBankAccount.domainEvents = domainEvents.toMutableList()
    return redisBankAccount
}
