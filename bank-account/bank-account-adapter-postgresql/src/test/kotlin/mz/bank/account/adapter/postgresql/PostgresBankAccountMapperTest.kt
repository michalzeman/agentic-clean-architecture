package mz.bank.account.adapter.postgresql

import mz.bank.account.domain.BankAccount
import mz.bank.account.domain.BankAccountAggregate
import mz.bank.account.domain.BankAccountEvent
import mz.bank.account.domain.Email
import mz.shared.domain.AggregateId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class PostgresBankAccountMapperTest {
    @Test
    fun `should convert PostgresBankAccount to BankAccount using relational refs`() {
        val aggregateUuid = UUID.randomUUID()
        val now = Instant.now()
        val accountRow =
            PostgresBankAccount(
                id = 1L,
                aggregateId = aggregateUuid,
                email = "test@example.com",
                amount = BigDecimal("150.00"),
                version = 5L,
                createdAt = now.minusSeconds(3600),
                updatedAt = now,
            )
        val refs =
            listOf(
                PostgresBankAccountTransactionRef(
                    bankAccountId = 1L,
                    transactionId = "txn-open-1",
                    state = TransactionRefState.OPENED.name,
                    createdAt = now,
                ),
                PostgresBankAccountTransactionRef(
                    bankAccountId = 1L,
                    transactionId = "txn-done-1",
                    state = TransactionRefState.FINISHED.name,
                    createdAt = now,
                ),
            )

        val bankAccount = accountRow.toBankAccount(refs)

        assertThat(bankAccount.aggregateId).isEqualTo(AggregateId(aggregateUuid.toString()))
        assertThat(bankAccount.email).isEqualTo(Email("test@example.com"))
        assertThat(bankAccount.amount).isEqualByComparingTo(BigDecimal("150.00"))
        assertThat(bankAccount.openedTransactions).containsExactly("txn-open-1")
        assertThat(bankAccount.finishedTransactions).containsExactly("txn-done-1")
        assertThat(bankAccount.version).isEqualTo(5L)
    }

    @Test
    fun `should throw when transaction ref has unknown state`() {
        val aggregateUuid = UUID.randomUUID()
        val now = Instant.now()
        val accountRow =
            PostgresBankAccount(
                id = 1L,
                aggregateId = aggregateUuid,
                email = "test@example.com",
                amount = BigDecimal("150.00"),
                version = 0L,
                createdAt = now,
                updatedAt = now,
            )
        val refs =
            listOf(
                PostgresBankAccountTransactionRef(
                    bankAccountId = 1L,
                    transactionId = "txn-1",
                    state = "UNKNOWN",
                    createdAt = now,
                ),
            )

        val thrown = assertThrows<IllegalStateException> { accountRow.toBankAccount(refs) }

        assertThat(thrown).hasMessageContaining("Unknown transaction state")
    }

    @Test
    fun `should convert PostgresBankAccount with empty refs`() {
        val aggregateUuid = UUID.randomUUID()
        val now = Instant.now()
        val accountRow =
            PostgresBankAccount(
                id = 1L,
                aggregateId = aggregateUuid,
                email = "empty@example.com",
                amount = BigDecimal.ZERO,
                version = 0L,
                createdAt = now,
                updatedAt = now,
            )

        val bankAccount = accountRow.toBankAccount(emptyList())

        assertThat(bankAccount.openedTransactions).isEmpty()
        assertThat(bankAccount.finishedTransactions).isEmpty()
    }

    @Test
    fun `should convert BankAccountAggregate to PostgresBankAccount correctly`() {
        val aggregateId = AggregateId(UUID.randomUUID().toString())
        val now = Instant.now()
        val bankAccount =
            BankAccount(
                aggregateId = aggregateId,
                email = Email("convert@example.com"),
                amount = BigDecimal("250.00"),
                openedTransactions = setOf("txn-open-1"),
                finishedTransactions = setOf("txn-done-1", "txn-done-2"),
                version = 3L,
                createdAt = now.minusSeconds(7200),
                updatedAt = now,
            )
        val domainEvents =
            listOf(
                BankAccountEvent.MoneyDeposited(aggregateId, now, BigDecimal("50.00")),
            )
        val aggregate = BankAccountAggregate(bankAccount, domainEvents)

        val postgresBankAccount = aggregate.toPostgresBankAccount()

        assertThat(postgresBankAccount.id).isNull()
        assertThat(postgresBankAccount.aggregateId).isEqualTo(UUID.fromString(aggregateId.value))
        assertThat(postgresBankAccount.email).isEqualTo("convert@example.com")
        assertThat(postgresBankAccount.amount).isEqualByComparingTo(BigDecimal("250.00"))
        assertThat(postgresBankAccount.version).isEqualTo(3L)
        assertThat(postgresBankAccount.createdAt).isEqualTo(now.minusSeconds(7200))
        assertThat(postgresBankAccount.updatedAt).isEqualTo(now)
    }

    @Test
    fun `should map transaction refs with states`() {
        val aggregateId = AggregateId(UUID.randomUUID().toString())
        val now = Instant.now()
        val bankAccount =
            BankAccount(
                aggregateId = aggregateId,
                email = Email("update@example.com"),
                amount = BigDecimal("100.00"),
                openedTransactions = setOf("txn-1"),
                finishedTransactions = setOf("txn-2"),
                version = 2L,
                createdAt = now,
                updatedAt = now,
            )
        val aggregate = BankAccountAggregate(bankAccount, emptyList())

        val refs = aggregate.toTransactionRefs(bankAccountId = 42L)

        assertThat(refs).hasSize(2)
        assertThat(refs.filter { it.state == TransactionRefState.OPENED.name }.map { it.transactionId })
            .containsExactly("txn-1")
        assertThat(refs.filter { it.state == TransactionRefState.FINISHED.name }.map { it.transactionId })
            .containsExactly("txn-2")
    }

    @Test
    fun `should map overlapping transaction id as finished only`() {
        val aggregateId = AggregateId(UUID.randomUUID().toString())
        val now = Instant.now()
        val overlappingTransactionId = "txn-overlap"
        val aggregate =
            BankAccountAggregate(
                account =
                    BankAccount(
                        aggregateId = aggregateId,
                        email = Email("overlap@example.com"),
                        amount = BigDecimal("100.00"),
                        openedTransactions = setOf(overlappingTransactionId),
                        version = 2L,
                        createdAt = now,
                        updatedAt = now,
                    ),
                domainEvents = emptyList(),
            )

        val refs = aggregate.toTransactionRefs(bankAccountId = 42L)

        val refsWithOverlapState =
            refs +
                PostgresBankAccountTransactionRef(
                    bankAccountId = 42L,
                    transactionId = overlappingTransactionId,
                    state = TransactionRefState.FINISHED.name,
                    createdAt = now,
                )

        val accountRow =
            PostgresBankAccount(
                id = 42L,
                aggregateId = UUID.fromString(aggregateId.value),
                email = "overlap@example.com",
                amount = BigDecimal("100.00"),
                version = 2L,
                createdAt = now,
                updatedAt = now,
            )

        val mapped = accountRow.toBankAccount(refsWithOverlapState)

        assertThat(mapped.openedTransactions).doesNotContain(overlappingTransactionId)
        assertThat(mapped.finishedTransactions).containsExactly(overlappingTransactionId)
    }

    @Test
    fun `should carry existingId forward when converting to PostgresBankAccount`() {
        val aggregateId = AggregateId(UUID.randomUUID().toString())
        val now = Instant.now()
        val bankAccount =
            BankAccount(
                aggregateId = aggregateId,
                email = Email("update@example.com"),
                amount = BigDecimal("100.00"),
                version = 2L,
                createdAt = now,
                updatedAt = now,
            )
        val aggregate = BankAccountAggregate(bankAccount, emptyList())

        val postgresBankAccount = aggregate.toPostgresBankAccount(existingId = 42L)

        assertThat(postgresBankAccount.id).isEqualTo(42L)
    }

    @Test
    fun `should return domain events from domainEvents method`() {
        val id = UUID.randomUUID()
        val now = Instant.now()
        val postgresBankAccount =
            PostgresBankAccount(
                id = 1L,
                aggregateId = id,
                email = "domainevents@example.com",
                amount = BigDecimal("100.00"),
                version = 0L,
                createdAt = now,
                updatedAt = now,
            )
        val aggregateId = AggregateId(id.toString())
        postgresBankAccount.domainEvents =
            mutableListOf(
                BankAccountEvent.MoneyDeposited(aggregateId, now, BigDecimal("50.00")),
                BankAccountEvent.MoneyWithdrawn(aggregateId, now, BigDecimal("20.00")),
            )

        val events = postgresBankAccount.domainEvents()

        assertThat(events).hasSize(2)
    }

    @Test
    fun `should clear domain events after publication`() {
        val id = UUID.randomUUID()
        val now = Instant.now()
        val postgresBankAccount =
            PostgresBankAccount(
                id = 1L,
                aggregateId = id,
                email = "clearevents@example.com",
                amount = BigDecimal("100.00"),
                version = 0L,
                createdAt = now,
                updatedAt = now,
            )
        val aggregateId = AggregateId(id.toString())
        postgresBankAccount.domainEvents =
            mutableListOf(
                BankAccountEvent.AccountCreated(aggregateId, now, Email("clearevents@example.com"), BigDecimal("100.00")),
            )

        postgresBankAccount.clearDomainEvents()

        assertThat(postgresBankAccount.domainEvents).isEmpty()
    }
}
