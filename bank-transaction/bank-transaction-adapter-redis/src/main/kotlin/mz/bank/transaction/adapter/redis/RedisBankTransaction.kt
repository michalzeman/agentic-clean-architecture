package mz.bank.transaction.adapter.redis

import mz.bank.transaction.domain.BankTransaction
import mz.bank.transaction.domain.BankTransactionAggregate
import mz.bank.transaction.domain.BankTransactionEvent
import mz.bank.transaction.domain.BankTransactionStatus
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
 * Redis persistence entity for BankTransaction.
 * Maps the domain entity to Redis storage using Spring Data Redis.
 */
@RedisHash("BankTransaction")
internal class RedisBankTransaction(
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
    var domainEvents: MutableList<BankTransactionEvent> = mutableListOf()

    @DomainEvents
    fun domainEvents(): Collection<BankTransactionEvent> = domainEvents.toList()

    @AfterDomainEventPublication
    fun clearDomainEvents() {
        domainEvents.clear()
    }
}

/**
 * Converts RedisBankTransaction persistence entity to BankTransaction domain entity.
 */
internal fun RedisBankTransaction.toBankTransaction(): BankTransaction =
    BankTransaction(
        aggregateId = AggregateId(id.toString()),
        correlationId = correlationId,
        fromAccountId = AggregateId(fromAccountId),
        toAccountId = AggregateId(toAccountId),
        amount = amount,
        moneyWithdrawn = moneyWithdrawn,
        moneyDeposited = moneyDeposited,
        status = BankTransactionStatus.valueOf(status),
        version = version,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

/**
 * Converts BankTransactionAggregate to RedisBankTransaction persistence entity.
 * Includes domain events for publication after save.
 */
internal fun BankTransactionAggregate.toRedisBankTransaction(): RedisBankTransaction {
    val redisBankTransaction =
        RedisBankTransaction(
            id = UUID.fromString(bankTransaction.aggregateId.value),
            correlationId = bankTransaction.correlationId,
            fromAccountId = bankTransaction.fromAccountId.value,
            toAccountId = bankTransaction.toAccountId.value,
            amount = bankTransaction.amount,
            moneyWithdrawn = bankTransaction.moneyWithdrawn,
            moneyDeposited = bankTransaction.moneyDeposited,
            status = bankTransaction.status.name,
            version = bankTransaction.version,
            createdAt = bankTransaction.createdAt,
            updatedAt = bankTransaction.updatedAt,
        )
    redisBankTransaction.domainEvents = domainEvents.toMutableList()
    return redisBankTransaction
}
