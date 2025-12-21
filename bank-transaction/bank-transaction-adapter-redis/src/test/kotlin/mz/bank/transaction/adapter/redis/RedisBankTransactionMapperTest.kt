package mz.bank.transaction.adapter.redis

import mz.bank.transaction.domain.BankTransaction
import mz.bank.transaction.domain.BankTransactionAggregate
import mz.bank.transaction.domain.BankTransactionEvent
import mz.bank.transaction.domain.BankTransactionStatus
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

class RedisBankTransactionMapperTest {
    // ==================== toBankTransaction() Mapping Tests ====================

    @Test
    fun `should convert RedisTransaction to Transaction correctly`() {
        // Given
        val id = UUID.randomUUID()
        val now = Instant.now()
        val redisBankTransaction =
            RedisBankTransaction(
                id = id,
                correlationId = "corr-mapping",
                fromAccountId = "acc-source",
                toAccountId = "acc-dest",
                amount = BigDecimal("1000.00"),
                moneyWithdrawn = true,
                moneyDeposited = true,
                status = BankTransactionStatus.FINISHED.name,
                version = 3L,
                createdAt = now.minusSeconds(3600),
                updatedAt = now,
            )

        // When
        val bankTransaction = redisBankTransaction.toBankTransaction()

        // Then
        assertThat(bankTransaction.aggregateId).isEqualTo(AggregateId(id.toString()))
        assertThat(bankTransaction.correlationId).isEqualTo("corr-mapping")
        assertThat(bankTransaction.fromAccountId).isEqualTo(AggregateId("acc-source"))
        assertThat(bankTransaction.toAccountId).isEqualTo(AggregateId("acc-dest"))
        assertThat(bankTransaction.amount).isEqualByComparingTo(BigDecimal("1000.00"))
        assertThat(bankTransaction.moneyWithdrawn).isTrue()
        assertThat(bankTransaction.moneyDeposited).isTrue()
        assertThat(bankTransaction.status).isEqualTo(BankTransactionStatus.FINISHED)
        assertThat(bankTransaction.version).isEqualTo(3L)
        assertThat(bankTransaction.createdAt).isEqualTo(now.minusSeconds(3600))
        assertThat(bankTransaction.updatedAt).isEqualTo(now)
    }

    @Test
    fun `should convert RedisTransaction with INITIALIZED status`() {
        // Given
        val id = UUID.randomUUID()
        val now = Instant.now()
        val redisBankTransaction =
            RedisBankTransaction(
                id = id,
                correlationId = "corr-init",
                fromAccountId = "acc-1",
                toAccountId = "acc-2",
                amount = BigDecimal("100.00"),
                moneyWithdrawn = false,
                moneyDeposited = false,
                status = BankTransactionStatus.INITIALIZED.name,
                version = 0L,
                createdAt = now,
                updatedAt = now,
            )

        // When
        val bankTransaction = redisBankTransaction.toBankTransaction()

        // Then
        assertThat(bankTransaction.status).isEqualTo(BankTransactionStatus.INITIALIZED)
        assertThat(bankTransaction.moneyWithdrawn).isFalse()
        assertThat(bankTransaction.moneyDeposited).isFalse()
    }

    @Test
    fun `should convert RedisTransaction with FAILED status`() {
        // Given
        val id = UUID.randomUUID()
        val now = Instant.now()
        val redisBankTransaction =
            RedisBankTransaction(
                id = id,
                correlationId = "corr-failed",
                fromAccountId = "acc-1",
                toAccountId = "acc-2",
                amount = BigDecimal("200.00"),
                moneyWithdrawn = false,
                moneyDeposited = false,
                status = BankTransactionStatus.FAILED.name,
                version = 5L,
                createdAt = now,
                updatedAt = now,
            )

        // When
        val bankTransaction = redisBankTransaction.toBankTransaction()

        // Then
        assertThat(bankTransaction.status).isEqualTo(BankTransactionStatus.FAILED)
        assertThat(bankTransaction.version).isEqualTo(5L)
    }

    // ==================== toRedisBankTransaction() Mapping Tests ====================

    @Test
    fun `should convert TransactionAggregate to RedisTransaction correctly`() {
        // Given
        val aggregateId = AggregateId(UUID.randomUUID().toString())
        val now = Instant.now()
        val bankTransaction =
            BankTransaction(
                aggregateId = aggregateId,
                correlationId = "corr-convert",
                fromAccountId = AggregateId("acc-src"),
                toAccountId = AggregateId("acc-dst"),
                amount = BigDecimal("2500.00"),
                moneyWithdrawn = true,
                moneyDeposited = false,
                status = BankTransactionStatus.CREATED,
                version = 2L,
                createdAt = now.minusSeconds(7200),
                updatedAt = now,
            )
        val domainEvents =
            listOf(
                BankTransactionEvent.BankTransactionCreated(
                    aggregateId = aggregateId,
                    correlationId = "corr-convert",
                    updatedAt = now,
                    fromAccountId = AggregateId("acc-src"),
                    toAccountId = AggregateId("acc-dst"),
                    amount = BigDecimal("2500.00"),
                ),
            )
        val aggregate = BankTransactionAggregate(bankTransaction, domainEvents)

        // When
        val redisBankTransaction = aggregate.toRedisBankTransaction()

        // Then
        assertThat(redisBankTransaction.id).isEqualTo(UUID.fromString(aggregateId.value))
        assertThat(redisBankTransaction.correlationId).isEqualTo("corr-convert")
        assertThat(redisBankTransaction.fromAccountId).isEqualTo("acc-src")
        assertThat(redisBankTransaction.toAccountId).isEqualTo("acc-dst")
        assertThat(redisBankTransaction.amount).isEqualByComparingTo(BigDecimal("2500.00"))
        assertThat(redisBankTransaction.moneyWithdrawn).isTrue()
        assertThat(redisBankTransaction.moneyDeposited).isFalse()
        assertThat(redisBankTransaction.status).isEqualTo(BankTransactionStatus.CREATED.name)
        assertThat(redisBankTransaction.version).isEqualTo(2L)
        assertThat(redisBankTransaction.createdAt).isEqualTo(now.minusSeconds(7200))
        assertThat(redisBankTransaction.updatedAt).isEqualTo(now)
    }

    @Test
    fun `should preserve domain events when converting to RedisTransaction`() {
        // Given
        val aggregateId = AggregateId(UUID.randomUUID().toString())
        val now = Instant.now()
        val bankTransaction =
            BankTransaction(
                aggregateId = aggregateId,
                correlationId = "corr-events-preserve",
                fromAccountId = AggregateId("acc-1"),
                toAccountId = AggregateId("acc-2"),
                amount = BigDecimal("500.00"),
                moneyWithdrawn = false,
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
                    correlationId = "corr-events-preserve",
                    updatedAt = now,
                    fromAccountId = AggregateId("acc-1"),
                    toAccountId = AggregateId("acc-2"),
                    amount = BigDecimal("500.00"),
                ),
                BankTransactionEvent.BankTransactionMoneyWithdrawn(
                    aggregateId = aggregateId,
                    correlationId = "corr-events-preserve",
                    updatedAt = now,
                ),
            )
        val aggregate = BankTransactionAggregate(bankTransaction, domainEvents)

        // When
        val redisBankTransaction = aggregate.toRedisBankTransaction()

        // Then
        assertThat(redisBankTransaction.domainEvents).hasSize(2)
        assertThat(redisBankTransaction.domainEvents.first()).isInstanceOf(BankTransactionEvent.BankTransactionCreated::class.java)
        assertThat(redisBankTransaction.domainEvents.last()).isInstanceOf(BankTransactionEvent.BankTransactionMoneyWithdrawn::class.java)
    }

    @Test
    fun `should convert aggregate with no domain events`() {
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

    // ==================== Round-trip Mapping Tests ====================

    @Test
    fun `should preserve data through round-trip conversion`() {
        // Given
        val aggregateId = AggregateId(UUID.randomUUID().toString())
        val now = Instant.now()
        val originalTransaction =
            BankTransaction(
                aggregateId = aggregateId,
                correlationId = "corr-roundtrip",
                fromAccountId = AggregateId("acc-rt-source"),
                toAccountId = AggregateId("acc-rt-dest"),
                amount = BigDecimal("9999.99"),
                moneyWithdrawn = true,
                moneyDeposited = true,
                status = BankTransactionStatus.FINISHED,
                version = 10L,
                createdAt = now.minusSeconds(5000),
                updatedAt = now,
            )
        val aggregate = BankTransactionAggregate(originalTransaction, emptyList())

        // When - convert to Redis and back
        val redisBankTransaction = aggregate.toRedisBankTransaction()
        val convertedBack = redisBankTransaction.toBankTransaction()

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
        status: BankTransactionStatus,
        withdrawn: Boolean,
        deposited: Boolean,
    ) {
        // Given
        val id = UUID.randomUUID()
        val now = Instant.now()
        val redisBankTransaction =
            RedisBankTransaction(
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
        val bankTransaction = redisBankTransaction.toBankTransaction()

        // Then
        assertThat(bankTransaction.status).isEqualTo(status)
        assertThat(bankTransaction.moneyWithdrawn).isEqualTo(withdrawn)
        assertThat(bankTransaction.moneyDeposited).isEqualTo(deposited)
    }

    companion object {
        @JvmStatic
        fun statusConversionTestCases(): Stream<Arguments> =
            Stream.of(
                Arguments.of(BankTransactionStatus.INITIALIZED, false, false),
                Arguments.of(BankTransactionStatus.CREATED, false, false),
                Arguments.of(BankTransactionStatus.CREATED, true, false),
                Arguments.of(BankTransactionStatus.CREATED, false, true),
                Arguments.of(BankTransactionStatus.CREATED, true, true),
                Arguments.of(BankTransactionStatus.FINISHED, true, true),
                Arguments.of(BankTransactionStatus.FAILED, false, false),
                Arguments.of(BankTransactionStatus.FAILED, true, false),
                Arguments.of(BankTransactionStatus.FAILED, false, true),
                Arguments.of(BankTransactionStatus.FAILED, true, true),
            )
    }
}
