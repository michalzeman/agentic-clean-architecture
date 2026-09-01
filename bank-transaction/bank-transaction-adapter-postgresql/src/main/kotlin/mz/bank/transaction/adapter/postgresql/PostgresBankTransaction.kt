package mz.bank.transaction.adapter.postgresql

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
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Spring Data JDBC persistence entity for BankTransaction.
 * Uses a BIGSERIAL surrogate PK and a separate aggregate_id UUID for domain identity.
 */
@Table("bank_transaction")
internal open class PostgresBankTransaction(
    @field:Id val id: Long? = null,
    @field:Column("aggregate_id") val aggregateId: UUID,
    @field:Column("correlation_id") val correlationId: String,
    @field:Column("from_account_id") val fromAccountId: UUID,
    @field:Column("to_account_id") val toAccountId: UUID,
    val amount: BigDecimal,
    @field:Column("money_withdrawn") val moneyWithdrawn: Boolean,
    @field:Column("money_deposited") val moneyDeposited: Boolean,
    @field:Column("withdraw_compensated") val withdrawCompensated: Boolean,
    @field:Column("deposit_compensated") val depositCompensated: Boolean,
    val status: String,
    @field:Column("aggregate_version") val aggregateVersion: Long,
    @field:Version val version: Long = 0L,
    @field:Column("created_at") val createdAt: Instant,
    @field:Column("updated_at") val updatedAt: Instant,
) {
    @Transient
    internal var domainEvents: MutableList<BankTransactionEvent> = mutableListOf()

    @DomainEvents
    open fun domainEvents(): Collection<BankTransactionEvent> = domainEvents.toList()

    @AfterDomainEventPublication
    open fun clearDomainEvents() {
        domainEvents.clear()
    }

    internal fun addDomainEvents(events: List<BankTransactionEvent>) {
        domainEvents.addAll(events)
    }
}

/**
 * Converts PostgresBankTransaction persistence entity to BankTransaction domain entity.
 * Maps aggregate_version (domain event-count) → domain version.
 * The version column (@Version optimistic-lock token) is invisible to the domain.
 */
internal fun PostgresBankTransaction.toBankTransaction(): BankTransaction =
    BankTransaction(
        aggregateId = AggregateId(aggregateId.toString()),
        correlationId = correlationId,
        fromAccountId = AggregateId(fromAccountId.toString()),
        toAccountId = AggregateId(toAccountId.toString()),
        amount = amount,
        moneyWithdrawn = moneyWithdrawn,
        moneyDeposited = moneyDeposited,
        withdrawCompensated = withdrawCompensated,
        depositCompensated = depositCompensated,
        status = BankTransactionStatus.valueOf(status),
        version = aggregateVersion,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

/**
 * Converts BankTransactionAggregate to PostgresBankTransaction persistence entity.
 * Carries the existing surrogate id forward for updates (null triggers INSERT).
 * Attaches domain events for publication after save.
 *
 * aggregateVersion: stores the domain event-count version as-is (V+N). The domain aggregate controls it.
 * existingVersion: pass the loaded row's version (@Version token) on UPDATE so Spring Data JDBC's
 *   @Version mechanism guards against concurrent modifications. On INSERT pass 0 (framework forces 1).
 */
internal fun BankTransactionAggregate.toPostgresBankTransaction(
    existingId: Long? = null,
    existingVersion: Long = 0L,
): PostgresBankTransaction {
    val entity =
        PostgresBankTransaction(
            id = existingId,
            aggregateId = UUID.fromString(bankTransaction.aggregateId.value),
            correlationId = bankTransaction.correlationId,
            fromAccountId = UUID.fromString(bankTransaction.fromAccountId.value),
            toAccountId = UUID.fromString(bankTransaction.toAccountId.value),
            amount = bankTransaction.amount,
            moneyWithdrawn = bankTransaction.moneyWithdrawn,
            moneyDeposited = bankTransaction.moneyDeposited,
            withdrawCompensated = bankTransaction.withdrawCompensated,
            depositCompensated = bankTransaction.depositCompensated,
            status = bankTransaction.status.name,
            aggregateVersion = bankTransaction.version,
            version = existingVersion,
            createdAt = bankTransaction.createdAt,
            updatedAt = bankTransaction.updatedAt,
        )
    entity.addDomainEvents(domainEvents)
    return entity
}
