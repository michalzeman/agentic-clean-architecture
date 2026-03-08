package mz.bank.account.adapter.postgresql

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
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Spring Data JDBC persistence entity for BankAccount.
 * Uses a BIGSERIAL surrogate PK and a separate aggregate_id UUID for domain identity.
 * Transaction references are stored in the separate bank_account_transaction_ref table.
 */
@Table("bank_account")
internal open class PostgresBankAccount(
    @field:Id val id: Long? = null,
    @field:Column("aggregate_id") val aggregateId: UUID,
    val email: String,
    val amount: BigDecimal,
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

internal enum class TransactionRefState {
    OPENED,
    FINISHED,
}

@Table("bank_account_transaction_ref")
internal data class PostgresBankAccountTransactionRef(
    @field:Id val id: Long? = null,
    @field:Column("bank_account_id") val bankAccountId: Long,
    @field:Column("transaction_id") val transactionId: String,
    val state: String,
    @field:Column("created_at") val createdAt: Instant,
)

private fun PostgresBankAccountTransactionRef.toState(): TransactionRefState =
    runCatching { TransactionRefState.valueOf(state) }
        .getOrElse {
            throw IllegalStateException("Unknown transaction state: $state")
        }

/**
 * Converts PostgresBankAccount persistence entity to BankAccount domain entity.
 * Maps transaction references to opened/finished transaction id sets.
 */
internal fun PostgresBankAccount.toBankAccount(transactionRefs: List<PostgresBankAccountTransactionRef> = emptyList()): BankAccount =
    transactionRefs.let { refs ->
        val finishedTransactions =
            refs
                .asSequence()
                .filter { it.toState() == TransactionRefState.FINISHED }
                .map { it.transactionId.toString() }
                .toSet()
        val openedTransactions =
            refs
                .asSequence()
                .filter { it.toState() == TransactionRefState.OPENED }
                .map { it.transactionId.toString() }
                .toSet() - finishedTransactions

        BankAccount(
            aggregateId = AggregateId(aggregateId.toString()),
            email = Email(email),
            amount = amount,
            openedTransactions = openedTransactions,
            finishedTransactions = finishedTransactions,
            version = version,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }

/**
 * Converts BankAccountAggregate to PostgresBankAccount persistence entity.
 * Carries the existing surrogate id forward for updates (null triggers INSERT).
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
            version = versionOverride,
            createdAt = account.createdAt,
            updatedAt = account.updatedAt,
        )
    entity.addDomainEvents(domainEvents)
    return entity
}

internal fun BankAccountAggregate.toTransactionRefs(bankAccountId: Long): List<PostgresBankAccountTransactionRef> {
    val finishedTransactions = account.finishedTransactions
    val openedTransactions = account.openedTransactions - finishedTransactions

    val openedRefs =
        openedTransactions.map { transactionId ->
            PostgresBankAccountTransactionRef(
                bankAccountId = bankAccountId,
                transactionId = transactionId,
                state = TransactionRefState.OPENED.name,
                createdAt = account.updatedAt,
            )
        }
    val finishedRefs =
        finishedTransactions.map { transactionId ->
            PostgresBankAccountTransactionRef(
                bankAccountId = bankAccountId,
                transactionId = transactionId,
                state = TransactionRefState.FINISHED.name,
                createdAt = account.updatedAt,
            )
        }

    return openedRefs + finishedRefs
}
