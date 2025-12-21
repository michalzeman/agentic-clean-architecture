package mz.bank.transaction.adapter.redis

import mz.bank.transaction.domain.Transaction
import mz.bank.transaction.domain.TransactionAggregate
import mz.bank.transaction.domain.TransactionEvent
import mz.bank.transaction.domain.TransactionStatus
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
 * Redis persistence entity for Transaction.
 * Maps the domain entity to Redis storage using Spring Data Redis.
 */
@RedisHash("Transaction")
internal class RedisTransaction(
    @field:Id val id: UUID,
    @field:Indexed val correlationId: String,
    val fromAccountId: String,
    val toAccountId: String,
    val amount: BigDecimal,
    val moneyWithdrawn: Boolean,
    val moneyDeposited: Boolean,
    val status: String,
    @field:Version val version: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    @Transient
    var domainEvents: MutableList<TransactionEvent> = mutableListOf()

    @DomainEvents
    fun domainEvents(): Collection<TransactionEvent> = domainEvents.toList()

    @AfterDomainEventPublication
    fun clearDomainEvents() {
        domainEvents.clear()
    }
}

/**
 * Converts RedisTransaction persistence entity to Transaction domain entity.
 */
internal fun RedisTransaction.toTransaction(): Transaction =
    Transaction(
        aggregateId = AggregateId(id.toString()),
        correlationId = correlationId,
        fromAccountId = AggregateId(fromAccountId),
        toAccountId = AggregateId(toAccountId),
        amount = amount,
        moneyWithdrawn = moneyWithdrawn,
        moneyDeposited = moneyDeposited,
        status = TransactionStatus.valueOf(status),
        version = version,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

/**
 * Converts TransactionAggregate to RedisTransaction persistence entity.
 * Includes domain events for publication after save.
 */
internal fun TransactionAggregate.toRedisTransaction(): RedisTransaction {
    val redisTransaction =
        RedisTransaction(
            id = UUID.fromString(transaction.aggregateId.value),
            correlationId = transaction.correlationId,
            fromAccountId = transaction.fromAccountId.value,
            toAccountId = transaction.toAccountId.value,
            amount = transaction.amount,
            moneyWithdrawn = transaction.moneyWithdrawn,
            moneyDeposited = transaction.moneyDeposited,
            status = transaction.status.name,
            version = transaction.version,
            createdAt = transaction.createdAt,
            updatedAt = transaction.updatedAt,
        )
    redisTransaction.domainEvents = domainEvents.toMutableList()
    return redisTransaction
}
