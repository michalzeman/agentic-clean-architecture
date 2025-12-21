package mz.bank.transaction.adapter.redis

import mz.bank.transaction.domain.Transaction
import mz.bank.transaction.domain.TransactionAggregate
import mz.bank.transaction.domain.TransactionEvent
import mz.bank.transaction.domain.TransactionStatus
import mz.shared.domain.AggregateId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class RedisTransactionDomainEventsTest {
    // ==================== Domain Events Collection Tests ====================

    @Test
    fun `should return domain events from domainEvents method`() {
        // Given
        val id = UUID.randomUUID()
        val now = Instant.now()
        val redisTransaction =
            RedisTransaction(
                id = id,
                correlationId = "corr-domain-events",
                fromAccountId = "acc-1",
                toAccountId = "acc-2",
                amount = BigDecimal("100.00"),
                moneyWithdrawn = false,
                moneyDeposited = false,
                status = TransactionStatus.CREATED.name,
                version = 0L,
                createdAt = now,
                updatedAt = now,
            )
        val aggregateId = AggregateId(id.toString())
        redisTransaction.domainEvents =
            mutableListOf(
                TransactionEvent.TransactionMoneyWithdrawn(aggregateId, "corr-domain-events", now),
                TransactionEvent.TransactionMoneyDeposited(aggregateId, "corr-domain-events", now),
            )

        // When
        val events = redisTransaction.domainEvents()

        // Then
        assertThat(events).hasSize(2)
        val eventList = events.toList()
        assertThat(eventList[0]).isInstanceOf(TransactionEvent.TransactionMoneyWithdrawn::class.java)
        assertThat(eventList[1]).isInstanceOf(TransactionEvent.TransactionMoneyDeposited::class.java)
    }

    @Test
    fun `should clear domain events after publication`() {
        // Given
        val id = UUID.randomUUID()
        val now = Instant.now()
        val redisTransaction =
            RedisTransaction(
                id = id,
                correlationId = "corr-clear-events",
                fromAccountId = "acc-1",
                toAccountId = "acc-2",
                amount = BigDecimal("100.00"),
                moneyWithdrawn = false,
                moneyDeposited = false,
                status = TransactionStatus.CREATED.name,
                version = 0L,
                createdAt = now,
                updatedAt = now,
            )
        val aggregateId = AggregateId(id.toString())
        redisTransaction.domainEvents =
            mutableListOf(
                TransactionEvent.TransactionCreated(
                    aggregateId,
                    "corr-clear-events",
                    now,
                    AggregateId("acc-1"),
                    AggregateId("acc-2"),
                    BigDecimal("100.00"),
                ),
            )

        // When
        redisTransaction.clearDomainEvents()

        // Then
        assertThat(redisTransaction.domainEvents).isEmpty()
    }

    // ==================== Domain Events Preservation Tests ====================

    @Test
    fun `should preserve domain events when converting aggregate to RedisTransaction`() {
        // Given
        val aggregateId = AggregateId(UUID.randomUUID().toString())
        val now = Instant.now()
        val transaction =
            Transaction(
                aggregateId = aggregateId,
                correlationId = "corr-preserve",
                fromAccountId = AggregateId("acc-1"),
                toAccountId = AggregateId("acc-2"),
                amount = BigDecimal("300.00"),
                moneyWithdrawn = true,
                moneyDeposited = false,
                status = TransactionStatus.CREATED,
                version = 1L,
                createdAt = now,
                updatedAt = now,
            )
        val domainEvents =
            listOf(
                TransactionEvent.TransactionCreated(
                    aggregateId = aggregateId,
                    correlationId = "corr-preserve",
                    updatedAt = now,
                    fromAccountId = AggregateId("acc-1"),
                    toAccountId = AggregateId("acc-2"),
                    amount = BigDecimal("300.00"),
                ),
                TransactionEvent.TransactionMoneyWithdrawn(
                    aggregateId = aggregateId,
                    correlationId = "corr-preserve",
                    updatedAt = now,
                ),
            )
        val aggregate = TransactionAggregate(transaction, domainEvents)

        // When
        val redisTransaction = aggregate.toRedisTransaction()

        // Then
        assertThat(redisTransaction.domainEvents).hasSize(2)
        assertThat(redisTransaction.domainEvents[0]).isInstanceOf(TransactionEvent.TransactionCreated::class.java)
        assertThat(redisTransaction.domainEvents[1]).isInstanceOf(TransactionEvent.TransactionMoneyWithdrawn::class.java)
    }

    @Test
    fun `should handle empty domain events when converting aggregate`() {
        // Given
        val aggregateId = AggregateId(UUID.randomUUID().toString())
        val now = Instant.now()
        val transaction =
            Transaction(
                aggregateId = aggregateId,
                correlationId = "corr-no-events",
                fromAccountId = AggregateId("acc-1"),
                toAccountId = AggregateId("acc-2"),
                amount = BigDecimal("100.00"),
                moneyWithdrawn = false,
                moneyDeposited = false,
                status = TransactionStatus.CREATED,
                version = 0L,
                createdAt = now,
                updatedAt = now,
            )
        val aggregate = TransactionAggregate(transaction, emptyList())

        // When
        val redisTransaction = aggregate.toRedisTransaction()

        // Then
        assertThat(redisTransaction.domainEvents).isEmpty()
    }

    @Test
    fun `should preserve multiple different event types`() {
        // Given
        val aggregateId = AggregateId(UUID.randomUUID().toString())
        val now = Instant.now()
        val transaction =
            Transaction(
                aggregateId = aggregateId,
                correlationId = "corr-multi-events",
                fromAccountId = AggregateId("acc-1"),
                toAccountId = AggregateId("acc-2"),
                amount = BigDecimal("500.00"),
                moneyWithdrawn = true,
                moneyDeposited = true,
                status = TransactionStatus.FINISHED,
                version = 3L,
                createdAt = now,
                updatedAt = now,
            )
        val domainEvents =
            listOf(
                TransactionEvent.TransactionCreated(
                    aggregateId = aggregateId,
                    correlationId = "corr-multi-events",
                    updatedAt = now,
                    fromAccountId = AggregateId("acc-1"),
                    toAccountId = AggregateId("acc-2"),
                    amount = BigDecimal("500.00"),
                ),
                TransactionEvent.TransactionMoneyWithdrawn(
                    aggregateId = aggregateId,
                    correlationId = "corr-multi-events",
                    updatedAt = now,
                ),
                TransactionEvent.TransactionMoneyDeposited(
                    aggregateId = aggregateId,
                    correlationId = "corr-multi-events",
                    updatedAt = now,
                ),
                TransactionEvent.TransactionFinished(
                    aggregateId = aggregateId,
                    correlationId = "corr-multi-events",
                    updatedAt = now,
                    fromAccountId = AggregateId("acc-1"),
                    toAccountId = AggregateId("acc-2"),
                ),
            )
        val aggregate = TransactionAggregate(transaction, domainEvents)

        // When
        val redisTransaction = aggregate.toRedisTransaction()

        // Then
        assertThat(redisTransaction.domainEvents).hasSize(4)
        assertThat(redisTransaction.domainEvents[0]).isInstanceOf(TransactionEvent.TransactionCreated::class.java)
        assertThat(redisTransaction.domainEvents[1]).isInstanceOf(TransactionEvent.TransactionMoneyWithdrawn::class.java)
        assertThat(redisTransaction.domainEvents[2]).isInstanceOf(TransactionEvent.TransactionMoneyDeposited::class.java)
        assertThat(redisTransaction.domainEvents[3]).isInstanceOf(TransactionEvent.TransactionFinished::class.java)
    }
}
