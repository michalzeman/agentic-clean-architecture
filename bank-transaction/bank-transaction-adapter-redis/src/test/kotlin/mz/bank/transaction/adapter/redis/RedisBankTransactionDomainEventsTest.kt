package mz.bank.transaction.adapter.redis

import mz.bank.transaction.domain.BankTransaction
import mz.bank.transaction.domain.BankTransactionAggregate
import mz.bank.transaction.domain.BankTransactionEvent
import mz.bank.transaction.domain.BankTransactionStatus
import mz.shared.domain.AggregateId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class RedisBankTransactionDomainEventsTest {
    // ==================== Domain Events Collection Tests ====================

    @Test
    fun `should return domain events from domainEvents method`() {
        // Given
        val id = UUID.randomUUID()
        val now = Instant.now()
        val redisBankTransaction =
            RedisBankTransaction(
                id = id,
                correlationId = "corr-domain-events",
                fromAccountId = "acc-1",
                toAccountId = "acc-2",
                amount = BigDecimal("100.00"),
                moneyWithdrawn = false,
                moneyDeposited = false,
                status = BankTransactionStatus.CREATED.name,
                version = 0L,
                createdAt = now,
                updatedAt = now,
            )
        val aggregateId = AggregateId(id.toString())
        redisBankTransaction.domainEvents =
            mutableListOf(
                BankTransactionEvent.BankTransactionMoneyWithdrawn(
                    aggregateId,
                    "corr-domain-events",
                    now,
                    AggregateId("acc-from"),
                    AggregateId("acc-to"),
                    BigDecimal("100.00"),
                ),
                BankTransactionEvent.BankTransactionMoneyDeposited(
                    aggregateId,
                    "corr-domain-events",
                    now,
                    AggregateId("acc-to"),
                ),
            )

        // When
        val events = redisBankTransaction.domainEvents()

        // Then
        assertThat(events).hasSize(2)
        val eventList = events.toList()
        assertThat(eventList[0]).isInstanceOf(BankTransactionEvent.BankTransactionMoneyWithdrawn::class.java)
        assertThat(eventList[1]).isInstanceOf(BankTransactionEvent.BankTransactionMoneyDeposited::class.java)
    }

    @Test
    fun `should clear domain events after publication`() {
        // Given
        val id = UUID.randomUUID()
        val now = Instant.now()
        val redisBankTransaction =
            RedisBankTransaction(
                id = id,
                correlationId = "corr-clear-events",
                fromAccountId = "acc-1",
                toAccountId = "acc-2",
                amount = BigDecimal("100.00"),
                moneyWithdrawn = false,
                moneyDeposited = false,
                status = BankTransactionStatus.CREATED.name,
                version = 0L,
                createdAt = now,
                updatedAt = now,
            )
        val aggregateId = AggregateId(id.toString())
        redisBankTransaction.domainEvents =
            mutableListOf(
                BankTransactionEvent.BankTransactionCreated(
                    aggregateId,
                    "corr-clear-events",
                    now,
                    AggregateId("acc-1"),
                    AggregateId("acc-2"),
                    BigDecimal("100.00"),
                ),
            )

        // When
        redisBankTransaction.clearDomainEvents()

        // Then
        assertThat(redisBankTransaction.domainEvents).isEmpty()
    }

    // ==================== Domain Events Preservation Tests ====================

    @Test
    fun `should preserve domain events when converting aggregate to RedisTransaction`() {
        // Given
        val aggregateId = AggregateId(UUID.randomUUID().toString())
        val now = Instant.now()
        val bankTransaction =
            BankTransaction(
                aggregateId = aggregateId,
                correlationId = "corr-preserve",
                fromAccountId = AggregateId("acc-1"),
                toAccountId = AggregateId("acc-2"),
                amount = BigDecimal("300.00"),
                moneyWithdrawn = true,
                moneyDeposited = false,
                status = BankTransactionStatus.CREATED,
                version = 1L,
                createdAt = now,
                updatedAt = now,
            )
        val domainEvents =
            listOf(
                BankTransactionEvent.BankTransactionCreated(
                    aggregateId = aggregateId,
                    correlationId = "corr-preserve",
                    updatedAt = now,
                    fromAccountId = AggregateId("acc-1"),
                    toAccountId = AggregateId("acc-2"),
                    amount = BigDecimal("300.00"),
                ),
                BankTransactionEvent.BankTransactionMoneyWithdrawn(
                    aggregateId = aggregateId,
                    correlationId = "corr-preserve",
                    updatedAt = now,
                    accountId = AggregateId("acc-1"),
                    toAccountId = AggregateId("acc-2"),
                    amount = BigDecimal("300.00"),
                ),
            )
        val aggregate = BankTransactionAggregate(bankTransaction, domainEvents)

        // When
        val redisBankTransaction = aggregate.toRedisBankTransaction()

        // Then
        assertThat(redisBankTransaction.domainEvents).hasSize(2)
        assertThat(redisBankTransaction.domainEvents[0]).isInstanceOf(BankTransactionEvent.BankTransactionCreated::class.java)
        assertThat(redisBankTransaction.domainEvents[1]).isInstanceOf(BankTransactionEvent.BankTransactionMoneyWithdrawn::class.java)
    }

    @Test
    fun `should handle empty domain events when converting aggregate`() {
        // Given
        val aggregateId = AggregateId(UUID.randomUUID().toString())
        val now = Instant.now()
        val bankTransaction =
            BankTransaction(
                aggregateId = aggregateId,
                correlationId = "corr-no-events",
                fromAccountId = AggregateId("acc-1"),
                toAccountId = AggregateId("acc-2"),
                amount = BigDecimal("100.00"),
                moneyWithdrawn = false,
                moneyDeposited = false,
                status = BankTransactionStatus.CREATED,
                version = 0L,
                createdAt = now,
                updatedAt = now,
            )
        val aggregate = BankTransactionAggregate(bankTransaction, emptyList())

        // When
        val redisBankTransaction = aggregate.toRedisBankTransaction()

        // Then
        assertThat(redisBankTransaction.domainEvents).isEmpty()
    }

    @Test
    fun `should preserve multiple different event types`() {
        // Given
        val aggregateId = AggregateId(UUID.randomUUID().toString())
        val now = Instant.now()
        val bankTransaction =
            BankTransaction(
                aggregateId = aggregateId,
                correlationId = "corr-multi-events",
                fromAccountId = AggregateId("acc-1"),
                toAccountId = AggregateId("acc-2"),
                amount = BigDecimal("500.00"),
                moneyWithdrawn = true,
                moneyDeposited = true,
                status = BankTransactionStatus.FINISHED,
                version = 3L,
                createdAt = now,
                updatedAt = now,
            )
        val domainEvents =
            listOf(
                BankTransactionEvent.BankTransactionCreated(
                    aggregateId = aggregateId,
                    correlationId = "corr-multi-events",
                    updatedAt = now,
                    fromAccountId = AggregateId("acc-1"),
                    toAccountId = AggregateId("acc-2"),
                    amount = BigDecimal("500.00"),
                ),
                BankTransactionEvent.BankTransactionMoneyWithdrawn(
                    aggregateId = aggregateId,
                    correlationId = "corr-multi-events",
                    updatedAt = now,
                    accountId = AggregateId("acc-1"),
                    toAccountId = AggregateId("acc-2"),
                    amount = BigDecimal("500.00"),
                ),
                BankTransactionEvent.BankTransactionMoneyDeposited(
                    aggregateId = aggregateId,
                    correlationId = "corr-multi-events",
                    updatedAt = now,
                    accountId = AggregateId("acc-2"),
                ),
                BankTransactionEvent.BankTransactionFinished(
                    aggregateId = aggregateId,
                    correlationId = "corr-multi-events",
                    updatedAt = now,
                    fromAccountId = AggregateId("acc-1"),
                    toAccountId = AggregateId("acc-2"),
                ),
            )
        val aggregate = BankTransactionAggregate(bankTransaction, domainEvents)

        // When
        val redisBankTransaction = aggregate.toRedisBankTransaction()

        // Then
        assertThat(redisBankTransaction.domainEvents).hasSize(4)
        assertThat(redisBankTransaction.domainEvents[0]).isInstanceOf(BankTransactionEvent.BankTransactionCreated::class.java)
        assertThat(redisBankTransaction.domainEvents[1]).isInstanceOf(BankTransactionEvent.BankTransactionMoneyWithdrawn::class.java)
        assertThat(redisBankTransaction.domainEvents[2]).isInstanceOf(BankTransactionEvent.BankTransactionMoneyDeposited::class.java)
        assertThat(redisBankTransaction.domainEvents[3]).isInstanceOf(BankTransactionEvent.BankTransactionFinished::class.java)
    }
}
