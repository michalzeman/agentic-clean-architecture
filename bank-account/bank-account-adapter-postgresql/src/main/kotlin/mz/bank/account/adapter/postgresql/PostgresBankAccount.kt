package mz.bank.account.adapter.postgresql

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import mz.bank.account.domain.BankAccount
import mz.bank.account.domain.BankAccountAggregate
import mz.bank.account.domain.BankAccountEvent
import mz.bank.account.domain.Email
import mz.shared.domain.AggregateId
import org.postgresql.util.PGobject
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

internal val bankAccountObjectMapper = jacksonObjectMapper()

/**
 * Spring Data JDBC persistence entity for BankAccount.
 * Uses a BIGSERIAL surrogate PK and a separate aggregate_id UUID for domain identity.
 * Set<String> fields are stored as JSONB columns. PGobject is used directly as the
 * field type so Spring Data JDBC passes it straight to the JDBC driver without
 * interference from collection-handling or custom converter resolution.
 */
@Table("bank_account")
internal open class PostgresBankAccount(
    @field:Id val id: Long? = null,
    @field:Column("aggregate_id") val aggregateId: UUID,
    val email: String,
    val amount: BigDecimal,
    @field:Column("opened_transactions") val openedTransactions: PGobject = jsonbOf("[]"),
    @field:Column("finished_transactions") val finishedTransactions: PGobject = jsonbOf("[]"),
    @field:Version val version: Long,
    @field:Column("created_at") val createdAt: Instant,
    @field:Column("updated_at") val updatedAt: Instant,
) {
    @Transient
    internal var domainEvents: MutableList<BankAccountEvent> = mutableListOf()

    @DomainEvents
    open fun domainEvents(): Collection<BankAccountEvent> = domainEvents.toList()

    @AfterDomainEventPublication
    open fun clearDomainEvents() {
        domainEvents.clear()
    }

    internal fun addDomainEvents(events: List<BankAccountEvent>) {
        domainEvents.addAll(events)
    }
}

/** Creates a JSONB-typed PGobject from a JSON string. */
internal fun jsonbOf(json: String): PGobject =
    PGobject().apply {
        type = "jsonb"
        value = json
    }

/**
 * Converts PostgresBankAccount persistence entity to BankAccount domain entity.
 * Deserializes the PGobject JSONB value back to Set<String>.
 */
internal fun PostgresBankAccount.toBankAccount(): BankAccount =
    BankAccount(
        aggregateId = AggregateId(aggregateId.toString()),
        email = Email(email),
        amount = amount,
        openedTransactions = bankAccountObjectMapper.readValue(openedTransactions.value ?: "[]"),
        finishedTransactions = bankAccountObjectMapper.readValue(finishedTransactions.value ?: "[]"),
        version = version,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

/**
 * Converts BankAccountAggregate to PostgresBankAccount persistence entity.
 * Carries the existing surrogate id forward for updates (null triggers INSERT).
 * Serializes Set<String> fields to PGobject (jsonb) for JSONB column storage.
 * Attaches domain events for publication after save.
 *
 * versionOverride: pass account.version - 1 on the UPDATE path so that Spring Data
 * JDBC's @Version mechanism sees the current DB version and generates the correct
 * WHERE version = ? guard, then increments it to account.version in the DB.
 */
internal fun BankAccountAggregate.toPostgresBankAccount(
    existingId: Long? = null,
    versionOverride: Long = account.version,
): PostgresBankAccount {
    val entity =
        PostgresBankAccount(
            id = existingId,
            aggregateId = UUID.fromString(account.aggregateId.value),
            email = account.email.value,
            amount = account.amount,
            openedTransactions = jsonbOf(bankAccountObjectMapper.writeValueAsString(account.openedTransactions)),
            finishedTransactions = jsonbOf(bankAccountObjectMapper.writeValueAsString(account.finishedTransactions)),
            version = versionOverride,
            createdAt = account.createdAt,
            updatedAt = account.updatedAt,
        )
    entity.addDomainEvents(domainEvents)
    return entity
}
