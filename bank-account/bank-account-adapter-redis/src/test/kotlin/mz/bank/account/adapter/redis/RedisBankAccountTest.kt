package mz.bank.account.adapter.redis

import mz.bank.account.domain.BankAccount
import mz.bank.account.domain.BankAccountAggregate
import mz.bank.account.domain.BankAccountEvent
import mz.bank.account.domain.Email
import mz.shared.domain.AggregateId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class RedisBankAccountTest {
    // ==================== toBankAccount() Mapping Tests ====================

    @Test
    fun `should convert RedisBankAccount to BankAccount correctly`() {
        // Given
        val id = UUID.randomUUID()
        val now = Instant.now()
        val redisBankAccount =
            RedisBankAccount(
                id = id,
                email = "test@example.com",
                amount = BigDecimal("150.00"),
                openedTransactions = setOf("txn-1", "txn-2"),
                finishedTransactions = setOf("txn-3"),
                version = 5L,
                createdAt = now.minusSeconds(3600),
                updatedAt = now,
            )

        // When
        val bankAccount = redisBankAccount.toBankAccount()

        // Then
        assertThat(bankAccount.aggregateId).isEqualTo(AggregateId(id.toString()))
        assertThat(bankAccount.email).isEqualTo(Email("test@example.com"))
        assertThat(bankAccount.amount).isEqualByComparingTo(BigDecimal("150.00"))
        assertThat(bankAccount.openedTransactions).containsExactlyInAnyOrder("txn-1", "txn-2")
        assertThat(bankAccount.finishedTransactions).containsExactly("txn-3")
        assertThat(bankAccount.version).isEqualTo(5L)
        assertThat(bankAccount.createdAt).isEqualTo(now.minusSeconds(3600))
        assertThat(bankAccount.updatedAt).isEqualTo(now)
    }

    @Test
    fun `should convert RedisBankAccount with empty transaction sets`() {
        // Given
        val id = UUID.randomUUID()
        val now = Instant.now()
        val redisBankAccount =
            RedisBankAccount(
                id = id,
                email = "empty@example.com",
                amount = BigDecimal.ZERO,
                openedTransactions = emptySet(),
                finishedTransactions = emptySet(),
                version = 0L,
                createdAt = now,
                updatedAt = now,
            )

        // When
        val bankAccount = redisBankAccount.toBankAccount()

        // Then
        assertThat(bankAccount.openedTransactions).isEmpty()
        assertThat(bankAccount.finishedTransactions).isEmpty()
    }

    // ==================== toRedisBankAccount() Mapping Tests ====================

    @Test
    fun `should convert BankAccountAggregate to RedisBankAccount correctly`() {
        // Given
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

        // When
        val redisBankAccount = aggregate.toRedisBankAccount()

        // Then
        assertThat(redisBankAccount.id).isEqualTo(UUID.fromString(aggregateId.value))
        assertThat(redisBankAccount.email).isEqualTo("convert@example.com")
        assertThat(redisBankAccount.amount).isEqualByComparingTo(BigDecimal("250.00"))
        assertThat(redisBankAccount.openedTransactions).containsExactly("txn-open-1")
        assertThat(redisBankAccount.finishedTransactions).containsExactlyInAnyOrder("txn-done-1", "txn-done-2")
        assertThat(redisBankAccount.version).isEqualTo(3L)
        assertThat(redisBankAccount.createdAt).isEqualTo(now.minusSeconds(7200))
        assertThat(redisBankAccount.updatedAt).isEqualTo(now)
    }

    @Test
    fun `should preserve domain events when converting to RedisBankAccount`() {
        // Given
        val aggregateId = AggregateId(UUID.randomUUID().toString())
        val now = Instant.now()
        val bankAccount =
            BankAccount(
                aggregateId = aggregateId,
                email = Email("events@example.com"),
                amount = BigDecimal("100.00"),
                version = 1L,
                createdAt = now,
                updatedAt = now,
            )
        val domainEvents =
            listOf(
                BankAccountEvent.AccountCreated(aggregateId, now, Email("events@example.com"), BigDecimal("100.00")),
            )
        val aggregate = BankAccountAggregate(bankAccount, domainEvents)

        // When
        val redisBankAccount = aggregate.toRedisBankAccount()

        // Then
        assertThat(redisBankAccount.domainEvents).hasSize(1)
        assertThat(redisBankAccount.domainEvents.first()).isInstanceOf(BankAccountEvent.AccountCreated::class.java)
    }

    @Test
    fun `should convert aggregate with no domain events`() {
        // Given
        val aggregateId = AggregateId(UUID.randomUUID().toString())
        val now = Instant.now()
        val bankAccount =
            BankAccount(
                aggregateId = aggregateId,
                email = Email("noevents@example.com"),
                amount = BigDecimal("500.00"),
                version = 10L,
                createdAt = now,
                updatedAt = now,
            )
        val aggregate = BankAccountAggregate(bankAccount, emptyList())

        // When
        val redisBankAccount = aggregate.toRedisBankAccount()

        // Then
        assertThat(redisBankAccount.domainEvents).isEmpty()
    }

    // ==================== Round-trip Mapping Tests ====================

    @Test
    fun `should preserve data through round-trip conversion`() {
        // Given
        val aggregateId = AggregateId(UUID.randomUUID().toString())
        val now = Instant.now()
        val originalAccount =
            BankAccount(
                aggregateId = aggregateId,
                email = Email("roundtrip@example.com"),
                amount = BigDecimal("999.99"),
                openedTransactions = setOf("txn-a", "txn-b"),
                finishedTransactions = setOf("txn-c"),
                version = 7L,
                createdAt = now.minusSeconds(1000),
                updatedAt = now,
            )
        val aggregate = BankAccountAggregate(originalAccount, emptyList())

        // When - convert to Redis and back
        val redisBankAccount = aggregate.toRedisBankAccount()
        val convertedBack = redisBankAccount.toBankAccount()

        // Then
        assertThat(convertedBack.aggregateId).isEqualTo(originalAccount.aggregateId)
        assertThat(convertedBack.email).isEqualTo(originalAccount.email)
        assertThat(convertedBack.amount).isEqualByComparingTo(originalAccount.amount)
        assertThat(convertedBack.openedTransactions).isEqualTo(originalAccount.openedTransactions)
        assertThat(convertedBack.finishedTransactions).isEqualTo(originalAccount.finishedTransactions)
        assertThat(convertedBack.version).isEqualTo(originalAccount.version)
        assertThat(convertedBack.createdAt).isEqualTo(originalAccount.createdAt)
        assertThat(convertedBack.updatedAt).isEqualTo(originalAccount.updatedAt)
    }

    // ==================== Domain Events Tests ====================

    @Test
    fun `should return domain events from domainEvents method`() {
        // Given
        val id = UUID.randomUUID()
        val now = Instant.now()
        val redisBankAccount =
            RedisBankAccount(
                id = id,
                email = "domainevents@example.com",
                amount = BigDecimal("100.00"),
                openedTransactions = emptySet(),
                finishedTransactions = emptySet(),
                version = 0L,
                createdAt = now,
                updatedAt = now,
            )
        val aggregateId = AggregateId(id.toString())
        redisBankAccount.domainEvents =
            mutableListOf(
                BankAccountEvent.MoneyDeposited(aggregateId, now, BigDecimal("50.00")),
                BankAccountEvent.MoneyWithdrawn(aggregateId, now, BigDecimal("20.00")),
            )

        // When
        val events = redisBankAccount.domainEvents()

        // Then
        assertThat(events).hasSize(2)
    }

    @Test
    fun `should clear domain events after publication`() {
        // Given
        val id = UUID.randomUUID()
        val now = Instant.now()
        val redisBankAccount =
            RedisBankAccount(
                id = id,
                email = "clearevents@example.com",
                amount = BigDecimal("100.00"),
                openedTransactions = emptySet(),
                finishedTransactions = emptySet(),
                version = 0L,
                createdAt = now,
                updatedAt = now,
            )
        val aggregateId = AggregateId(id.toString())
        redisBankAccount.domainEvents =
            mutableListOf(
                BankAccountEvent.AccountCreated(aggregateId, now, Email("clearevents@example.com"), BigDecimal("100.00")),
            )

        // When
        redisBankAccount.clearDomainEvents()

        // Then
        assertThat(redisBankAccount.domainEvents).isEmpty()
    }
}
