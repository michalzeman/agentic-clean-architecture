package mz.bank.transaction.adapter.redis

import mz.bank.transaction.domain.Transaction
import mz.bank.transaction.domain.TransactionAggregate
import mz.bank.transaction.domain.TransactionEvent
import mz.bank.transaction.domain.TransactionStatus
import mz.shared.domain.AggregateId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import java.util.stream.Stream

class RedisTransactionMapperTest {
    // ==================== toTransaction() Mapping Tests ====================

    @Test
    fun `should convert RedisTransaction to Transaction correctly`() {
        // Given
        val id = UUID.randomUUID()
        val now = Instant.now()
        val redisTransaction =
            RedisTransaction(
                id = id,
                correlationId = "corr-mapping",
                fromAccountId = "acc-source",
                toAccountId = "acc-dest",
                amount = BigDecimal("1000.00"),
                moneyWithdrawn = true,
                moneyDeposited = true,
                status = TransactionStatus.FINISHED.name,
                version = 3L,
                createdAt = now.minusSeconds(3600),
                updatedAt = now,
            )

        // When
        val transaction = redisTransaction.toTransaction()

        // Then
        assertThat(transaction.aggregateId).isEqualTo(AggregateId(id.toString()))
        assertThat(transaction.correlationId).isEqualTo("corr-mapping")
        assertThat(transaction.fromAccountId).isEqualTo(AggregateId("acc-source"))
        assertThat(transaction.toAccountId).isEqualTo(AggregateId("acc-dest"))
        assertThat(transaction.amount).isEqualByComparingTo(BigDecimal("1000.00"))
        assertThat(transaction.moneyWithdrawn).isTrue()
        assertThat(transaction.moneyDeposited).isTrue()
        assertThat(transaction.status).isEqualTo(TransactionStatus.FINISHED)
        assertThat(transaction.version).isEqualTo(3L)
        assertThat(transaction.createdAt).isEqualTo(now.minusSeconds(3600))
        assertThat(transaction.updatedAt).isEqualTo(now)
    }

    @Test
    fun `should convert RedisTransaction with INITIALIZED status`() {
        // Given
        val id = UUID.randomUUID()
        val now = Instant.now()
        val redisTransaction =
            RedisTransaction(
                id = id,
                correlationId = "corr-init",
                fromAccountId = "acc-1",
                toAccountId = "acc-2",
                amount = BigDecimal("100.00"),
                moneyWithdrawn = false,
                moneyDeposited = false,
                status = TransactionStatus.INITIALIZED.name,
                version = 0L,
                createdAt = now,
                updatedAt = now,
            )

        // When
        val transaction = redisTransaction.toTransaction()

        // Then
        assertThat(transaction.status).isEqualTo(TransactionStatus.INITIALIZED)
        assertThat(transaction.moneyWithdrawn).isFalse()
        assertThat(transaction.moneyDeposited).isFalse()
    }

    @Test
    fun `should convert RedisTransaction with FAILED status`() {
        // Given
        val id = UUID.randomUUID()
        val now = Instant.now()
        val redisTransaction =
            RedisTransaction(
                id = id,
                correlationId = "corr-failed",
                fromAccountId = "acc-1",
                toAccountId = "acc-2",
                amount = BigDecimal("200.00"),
                moneyWithdrawn = false,
                moneyDeposited = false,
                status = TransactionStatus.FAILED.name,
                version = 5L,
                createdAt = now,
                updatedAt = now,
            )

        // When
        val transaction = redisTransaction.toTransaction()

        // Then
        assertThat(transaction.status).isEqualTo(TransactionStatus.FAILED)
        assertThat(transaction.version).isEqualTo(5L)
    }

    // ==================== toRedisTransaction() Mapping Tests ====================

    @Test
    fun `should convert TransactionAggregate to RedisTransaction correctly`() {
        // Given
        val aggregateId = AggregateId(UUID.randomUUID().toString())
        val now = Instant.now()
        val transaction =
            Transaction(
                aggregateId = aggregateId,
                correlationId = "corr-convert",
                fromAccountId = AggregateId("acc-src"),
                toAccountId = AggregateId("acc-dst"),
                amount = BigDecimal("2500.00"),
                moneyWithdrawn = true,
                moneyDeposited = false,
                status = TransactionStatus.CREATED,
                version = 2L,
                createdAt = now.minusSeconds(7200),
                updatedAt = now,
            )
        val domainEvents =
            listOf(
                TransactionEvent.TransactionCreated(
                    aggregateId = aggregateId,
                    correlationId = "corr-convert",
                    updatedAt = now,
                    fromAccountId = AggregateId("acc-src"),
                    toAccountId = AggregateId("acc-dst"),
                    amount = BigDecimal("2500.00"),
                ),
            )
        val aggregate = TransactionAggregate(transaction, domainEvents)

        // When
        val redisTransaction = aggregate.toRedisTransaction()

        // Then
        assertThat(redisTransaction.id).isEqualTo(UUID.fromString(aggregateId.value))
        assertThat(redisTransaction.correlationId).isEqualTo("corr-convert")
        assertThat(redisTransaction.fromAccountId).isEqualTo("acc-src")
        assertThat(redisTransaction.toAccountId).isEqualTo("acc-dst")
        assertThat(redisTransaction.amount).isEqualByComparingTo(BigDecimal("2500.00"))
        assertThat(redisTransaction.moneyWithdrawn).isTrue()
        assertThat(redisTransaction.moneyDeposited).isFalse()
        assertThat(redisTransaction.status).isEqualTo(TransactionStatus.CREATED.name)
        assertThat(redisTransaction.version).isEqualTo(2L)
        assertThat(redisTransaction.createdAt).isEqualTo(now.minusSeconds(7200))
        assertThat(redisTransaction.updatedAt).isEqualTo(now)
    }

    @Test
    fun `should preserve domain events when converting to RedisTransaction`() {
        // Given
        val aggregateId = AggregateId(UUID.randomUUID().toString())
        val now = Instant.now()
        val transaction =
            Transaction(
                aggregateId = aggregateId,
                correlationId = "corr-events-preserve",
                fromAccountId = AggregateId("acc-1"),
                toAccountId = AggregateId("acc-2"),
                amount = BigDecimal("500.00"),
                moneyWithdrawn = false,
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
                    correlationId = "corr-events-preserve",
                    updatedAt = now,
                    fromAccountId = AggregateId("acc-1"),
                    toAccountId = AggregateId("acc-2"),
                    amount = BigDecimal("500.00"),
                ),
                TransactionEvent.TransactionMoneyWithdrawn(
                    aggregateId = aggregateId,
                    correlationId = "corr-events-preserve",
                    updatedAt = now,
                ),
            )
        val aggregate = TransactionAggregate(transaction, domainEvents)

        // When
        val redisTransaction = aggregate.toRedisTransaction()

        // Then
        assertThat(redisTransaction.domainEvents).hasSize(2)
        assertThat(redisTransaction.domainEvents.first()).isInstanceOf(TransactionEvent.TransactionCreated::class.java)
        assertThat(redisTransaction.domainEvents.last()).isInstanceOf(TransactionEvent.TransactionMoneyWithdrawn::class.java)
    }

    @Test
    fun `should convert aggregate with no domain events`() {
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

    // ==================== Round-trip Mapping Tests ====================

    @Test
    fun `should preserve data through round-trip conversion`() {
        // Given
        val aggregateId = AggregateId(UUID.randomUUID().toString())
        val now = Instant.now()
        val originalTransaction =
            Transaction(
                aggregateId = aggregateId,
                correlationId = "corr-roundtrip",
                fromAccountId = AggregateId("acc-rt-source"),
                toAccountId = AggregateId("acc-rt-dest"),
                amount = BigDecimal("9999.99"),
                moneyWithdrawn = true,
                moneyDeposited = true,
                status = TransactionStatus.FINISHED,
                version = 10L,
                createdAt = now.minusSeconds(5000),
                updatedAt = now,
            )
        val aggregate = TransactionAggregate(originalTransaction, emptyList())

        // When - convert to Redis and back
        val redisTransaction = aggregate.toRedisTransaction()
        val convertedBack = redisTransaction.toTransaction()

        // Then
        assertThat(convertedBack.aggregateId).isEqualTo(originalTransaction.aggregateId)
        assertThat(convertedBack.correlationId).isEqualTo(originalTransaction.correlationId)
        assertThat(convertedBack.fromAccountId).isEqualTo(originalTransaction.fromAccountId)
        assertThat(convertedBack.toAccountId).isEqualTo(originalTransaction.toAccountId)
        assertThat(convertedBack.amount).isEqualByComparingTo(originalTransaction.amount)
        assertThat(convertedBack.moneyWithdrawn).isEqualTo(originalTransaction.moneyWithdrawn)
        assertThat(convertedBack.moneyDeposited).isEqualTo(originalTransaction.moneyDeposited)
        assertThat(convertedBack.status).isEqualTo(originalTransaction.status)
        assertThat(convertedBack.version).isEqualTo(originalTransaction.version)
        assertThat(convertedBack.createdAt).isEqualTo(originalTransaction.createdAt)
        assertThat(convertedBack.updatedAt).isEqualTo(originalTransaction.updatedAt)
    }

    @ParameterizedTest(name = "status={0}, withdrawn={1}, deposited={2}")
    @MethodSource("statusConversionTestCases")
    fun `should handle status conversions correctly`(
        status: TransactionStatus,
        withdrawn: Boolean,
        deposited: Boolean,
    ) {
        // Given
        val id = UUID.randomUUID()
        val now = Instant.now()
        val redisTransaction =
            RedisTransaction(
                id = id,
                correlationId = "corr-status-test",
                fromAccountId = "acc-1",
                toAccountId = "acc-2",
                amount = BigDecimal("100.00"),
                moneyWithdrawn = withdrawn,
                moneyDeposited = deposited,
                status = status.name,
                version = 0L,
                createdAt = now,
                updatedAt = now,
            )

        // When
        val transaction = redisTransaction.toTransaction()

        // Then
        assertThat(transaction.status).isEqualTo(status)
        assertThat(transaction.moneyWithdrawn).isEqualTo(withdrawn)
        assertThat(transaction.moneyDeposited).isEqualTo(deposited)
    }

    companion object {
        @JvmStatic
        fun statusConversionTestCases(): Stream<Arguments> =
            Stream.of(
                Arguments.of(TransactionStatus.INITIALIZED, false, false),
                Arguments.of(TransactionStatus.CREATED, false, false),
                Arguments.of(TransactionStatus.CREATED, true, false),
                Arguments.of(TransactionStatus.CREATED, false, true),
                Arguments.of(TransactionStatus.CREATED, true, true),
                Arguments.of(TransactionStatus.FINISHED, true, true),
                Arguments.of(TransactionStatus.FAILED, false, false),
                Arguments.of(TransactionStatus.FAILED, true, false),
                Arguments.of(TransactionStatus.FAILED, false, true),
                Arguments.of(TransactionStatus.FAILED, true, true),
            )
    }
}
