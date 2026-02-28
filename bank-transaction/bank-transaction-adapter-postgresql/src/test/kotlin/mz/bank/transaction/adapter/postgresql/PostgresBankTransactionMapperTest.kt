package mz.bank.transaction.adapter.postgresql

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

class PostgresBankTransactionMapperTest {
    // ==================== toBankTransaction() Mapping Tests ====================

    @Test
    fun `should convert PostgresBankTransaction to BankTransaction correctly`() {
        // Given
        val id = UUID.randomUUID()
        val fromAccountId = UUID.randomUUID()
        val toAccountId = UUID.randomUUID()
        val now = Instant.now()
        val postgresBankTransaction =
            PostgresBankTransaction(
                id = 1L,
                aggregateId = id,
                correlationId = "corr-mapping",
                fromAccountId = fromAccountId,
                toAccountId = toAccountId,
                amount = BigDecimal("1000.00"),
                moneyWithdrawn = true,
                moneyDeposited = true,
                status = BankTransactionStatus.FINISHED.name,
                version = 3L,
                createdAt = now.minusSeconds(3600),
                updatedAt = now,
            )

        // When
        val bankTransaction = postgresBankTransaction.toBankTransaction()

        // Then
        assertThat(bankTransaction.aggregateId).isEqualTo(AggregateId(id.toString()))
        assertThat(bankTransaction.correlationId).isEqualTo("corr-mapping")
        assertThat(bankTransaction.fromAccountId).isEqualTo(AggregateId(fromAccountId.toString()))
        assertThat(bankTransaction.toAccountId).isEqualTo(AggregateId(toAccountId.toString()))
        assertThat(bankTransaction.amount).isEqualByComparingTo(BigDecimal("1000.00"))
        assertThat(bankTransaction.moneyWithdrawn).isTrue()
        assertThat(bankTransaction.moneyDeposited).isTrue()
        assertThat(bankTransaction.status).isEqualTo(BankTransactionStatus.FINISHED)
        assertThat(bankTransaction.version).isEqualTo(3L)
        assertThat(bankTransaction.createdAt).isEqualTo(now.minusSeconds(3600))
        assertThat(bankTransaction.updatedAt).isEqualTo(now)
    }

    @Test
    fun `should convert PostgresBankTransaction with INITIALIZED status`() {
        // Given
        val id = UUID.randomUUID()
        val fromAccountId = UUID.randomUUID()
        val toAccountId = UUID.randomUUID()
        val now = Instant.now()
        val postgresBankTransaction =
            PostgresBankTransaction(
                id = 1L,
                aggregateId = id,
                correlationId = "corr-init",
                fromAccountId = fromAccountId,
                toAccountId = toAccountId,
                amount = BigDecimal("100.00"),
                moneyWithdrawn = false,
                moneyDeposited = false,
                status = BankTransactionStatus.INITIALIZED.name,
                version = 0L,
                createdAt = now,
                updatedAt = now,
            )

        // When
        val bankTransaction = postgresBankTransaction.toBankTransaction()

        // Then
        assertThat(bankTransaction.status).isEqualTo(BankTransactionStatus.INITIALIZED)
        assertThat(bankTransaction.moneyWithdrawn).isFalse()
        assertThat(bankTransaction.moneyDeposited).isFalse()
    }

    @Test
    fun `should convert PostgresBankTransaction with FAILED status`() {
        // Given
        val id = UUID.randomUUID()
        val fromAccountId = UUID.randomUUID()
        val toAccountId = UUID.randomUUID()
        val now = Instant.now()
        val postgresBankTransaction =
            PostgresBankTransaction(
                id = 1L,
                aggregateId = id,
                correlationId = "corr-failed",
                fromAccountId = fromAccountId,
                toAccountId = toAccountId,
                amount = BigDecimal("200.00"),
                moneyWithdrawn = false,
                moneyDeposited = false,
                status = BankTransactionStatus.FAILED.name,
                version = 5L,
                createdAt = now,
                updatedAt = now,
            )

        // When
        val bankTransaction = postgresBankTransaction.toBankTransaction()

        // Then
        assertThat(bankTransaction.status).isEqualTo(BankTransactionStatus.FAILED)
        assertThat(bankTransaction.version).isEqualTo(5L)
    }

    // ==================== toPostgresBankTransaction() Mapping Tests ====================

    @Test
    fun `should convert BankTransactionAggregate to PostgresBankTransaction correctly`() {
        // Given
        val aggregateId = AggregateId(UUID.randomUUID().toString())
        val fromAccountUUID = UUID.randomUUID()
        val toAccountUUID = UUID.randomUUID()
        val now = Instant.now()
        val bankTransaction =
            BankTransaction(
                aggregateId = aggregateId,
                correlationId = "corr-convert",
                fromAccountId = AggregateId(fromAccountUUID.toString()),
                toAccountId = AggregateId(toAccountUUID.toString()),
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
                    fromAccountId = AggregateId(fromAccountUUID.toString()),
                    toAccountId = AggregateId(toAccountUUID.toString()),
                    amount = BigDecimal("2500.00"),
                ),
            )
        val aggregate = BankTransactionAggregate(bankTransaction, domainEvents)

        // When
        val postgresBankTransaction = aggregate.toPostgresBankTransaction()

        // Then
        assertThat(postgresBankTransaction.id).isNull()
        assertThat(postgresBankTransaction.aggregateId).isEqualTo(UUID.fromString(aggregateId.value))
        assertThat(postgresBankTransaction.correlationId).isEqualTo("corr-convert")
        assertThat(postgresBankTransaction.fromAccountId).isEqualTo(fromAccountUUID)
        assertThat(postgresBankTransaction.toAccountId).isEqualTo(toAccountUUID)
        assertThat(postgresBankTransaction.amount).isEqualByComparingTo(BigDecimal("2500.00"))
        assertThat(postgresBankTransaction.moneyWithdrawn).isTrue()
        assertThat(postgresBankTransaction.moneyDeposited).isFalse()
        assertThat(postgresBankTransaction.status).isEqualTo(BankTransactionStatus.CREATED.name)
        assertThat(postgresBankTransaction.version).isEqualTo(2L)
        assertThat(postgresBankTransaction.createdAt).isEqualTo(now.minusSeconds(7200))
        assertThat(postgresBankTransaction.updatedAt).isEqualTo(now)
    }

    @Test
    fun `should carry existingId forward when converting to PostgresBankTransaction`() {
        // Given
        val aggregateId = AggregateId(UUID.randomUUID().toString())
        val fromAccountUUID = UUID.randomUUID()
        val toAccountUUID = UUID.randomUUID()
        val now = Instant.now()
        val bankTransaction =
            BankTransaction(
                aggregateId = aggregateId,
                correlationId = "corr-update",
                fromAccountId = AggregateId(fromAccountUUID.toString()),
                toAccountId = AggregateId(toAccountUUID.toString()),
                amount = BigDecimal("100.00"),
                version = 1L,
                createdAt = now,
                updatedAt = now,
            )
        val aggregate = BankTransactionAggregate(bankTransaction, emptyList())

        // When
        val postgresBankTransaction = aggregate.toPostgresBankTransaction(existingId = 99L)

        // Then
        assertThat(postgresBankTransaction.id).isEqualTo(99L)
    }

    @Test
    fun `should preserve domain events when converting to PostgresBankTransaction`() {
        // Given
        val aggregateId = AggregateId(UUID.randomUUID().toString())
        val fromAccountUUID = UUID.randomUUID()
        val toAccountUUID = UUID.randomUUID()
        val now = Instant.now()
        val bankTransaction =
            BankTransaction(
                aggregateId = aggregateId,
                correlationId = "corr-events",
                fromAccountId = AggregateId(fromAccountUUID.toString()),
                toAccountId = AggregateId(toAccountUUID.toString()),
                amount = BigDecimal("500.00"),
                version = 1L,
                createdAt = now,
                updatedAt = now,
            )
        val domainEvents =
            listOf(
                BankTransactionEvent.BankTransactionCreated(
                    aggregateId = aggregateId,
                    correlationId = "corr-events",
                    updatedAt = now,
                    fromAccountId = AggregateId("acc-1"),
                    toAccountId = AggregateId("acc-2"),
                    amount = BigDecimal("500.00"),
                ),
                BankTransactionEvent.BankTransactionMoneyWithdrawn(
                    aggregateId = aggregateId,
                    correlationId = "corr-events",
                    updatedAt = now,
                    accountId = AggregateId("acc-1"),
                    toAccountId = AggregateId("acc-2"),
                    amount = BigDecimal("500.00"),
                ),
            )
        val aggregate = BankTransactionAggregate(bankTransaction, domainEvents)

        // When
        val postgresBankTransaction = aggregate.toPostgresBankTransaction()

        // Then
        assertThat(postgresBankTransaction.domainEvents).hasSize(2)
        assertThat(postgresBankTransaction.domainEvents.first()).isInstanceOf(BankTransactionEvent.BankTransactionCreated::class.java)
        assertThat(postgresBankTransaction.domainEvents.last()).isInstanceOf(BankTransactionEvent.BankTransactionMoneyWithdrawn::class.java)
    }

    @Test
    fun `should convert aggregate with no domain events`() {
        // Given
        val aggregateId = AggregateId(UUID.randomUUID().toString())
        val fromAccountUUID = UUID.randomUUID()
        val toAccountUUID = UUID.randomUUID()
        val now = Instant.now()
        val bankTransaction =
            BankTransaction(
                aggregateId = aggregateId,
                correlationId = "corr-no-events",
                fromAccountId = AggregateId(fromAccountUUID.toString()),
                toAccountId = AggregateId(toAccountUUID.toString()),
                amount = BigDecimal("100.00"),
                version = 0L,
                createdAt = now,
                updatedAt = now,
            )
        val aggregate = BankTransactionAggregate(bankTransaction, emptyList())

        // When
        val postgresBankTransaction = aggregate.toPostgresBankTransaction()

        // Then
        assertThat(postgresBankTransaction.domainEvents).isEmpty()
    }

    // ==================== Round-trip Mapping Tests ====================

    @Test
    fun `should preserve data through round-trip conversion`() {
        // Given
        val aggregateId = AggregateId(UUID.randomUUID().toString())
        val fromAccountUUID = UUID.randomUUID()
        val toAccountUUID = UUID.randomUUID()
        val now = Instant.now()
        val originalTransaction =
            BankTransaction(
                aggregateId = aggregateId,
                correlationId = "corr-roundtrip",
                fromAccountId = AggregateId(fromAccountUUID.toString()),
                toAccountId = AggregateId(toAccountUUID.toString()),
                amount = BigDecimal("9999.99"),
                moneyWithdrawn = true,
                moneyDeposited = true,
                status = BankTransactionStatus.FINISHED,
                version = 10L,
                createdAt = now.minusSeconds(5000),
                updatedAt = now,
            )
        val aggregate = BankTransactionAggregate(originalTransaction, emptyList())

        // When - convert to Postgres entity and back
        val postgresBankTransaction = aggregate.toPostgresBankTransaction()
        val convertedBack = postgresBankTransaction.toBankTransaction()

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

    // ==================== Domain Events Tests ====================

    @Test
    fun `should return domain events from domainEvents method`() {
        // Given
        val id = UUID.randomUUID()
        val aggregateId = AggregateId(id.toString())
        val fromAccountId = UUID.randomUUID()
        val toAccountId = UUID.randomUUID()
        val now = Instant.now()
        val postgresBankTransaction =
            PostgresBankTransaction(
                id = 1L,
                aggregateId = id,
                correlationId = "corr-de",
                fromAccountId = fromAccountId,
                toAccountId = toAccountId,
                amount = BigDecimal("100.00"),
                moneyWithdrawn = false,
                moneyDeposited = false,
                status = BankTransactionStatus.CREATED.name,
                version = 0L,
                createdAt = now,
                updatedAt = now,
            )
        postgresBankTransaction.domainEvents =
            mutableListOf(
                BankTransactionEvent.BankTransactionCreated(
                    aggregateId = aggregateId,
                    correlationId = "corr-de",
                    updatedAt = now,
                    fromAccountId = AggregateId("acc-1"),
                    toAccountId = AggregateId("acc-2"),
                    amount = BigDecimal("100.00"),
                ),
                BankTransactionEvent.BankTransactionMoneyWithdrawn(
                    aggregateId = aggregateId,
                    correlationId = "corr-de",
                    updatedAt = now,
                    accountId = AggregateId("acc-1"),
                    toAccountId = AggregateId("acc-2"),
                    amount = BigDecimal("100.00"),
                ),
            )

        // When
        val events = postgresBankTransaction.domainEvents()

        // Then
        assertThat(events).hasSize(2)
    }

    @Test
    fun `should clear domain events after publication`() {
        // Given
        val id = UUID.randomUUID()
        val aggregateId = AggregateId(id.toString())
        val fromAccountId = UUID.randomUUID()
        val toAccountId = UUID.randomUUID()
        val now = Instant.now()
        val postgresBankTransaction =
            PostgresBankTransaction(
                id = 1L,
                aggregateId = id,
                correlationId = "corr-clear",
                fromAccountId = fromAccountId,
                toAccountId = toAccountId,
                amount = BigDecimal("100.00"),
                moneyWithdrawn = false,
                moneyDeposited = false,
                status = BankTransactionStatus.CREATED.name,
                version = 0L,
                createdAt = now,
                updatedAt = now,
            )
        postgresBankTransaction.domainEvents =
            mutableListOf(
                BankTransactionEvent.BankTransactionCreated(
                    aggregateId = aggregateId,
                    correlationId = "corr-clear",
                    updatedAt = now,
                    fromAccountId = AggregateId("acc-1"),
                    toAccountId = AggregateId("acc-2"),
                    amount = BigDecimal("100.00"),
                ),
            )

        // When
        postgresBankTransaction.clearDomainEvents()

        // Then
        assertThat(postgresBankTransaction.domainEvents).isEmpty()
    }

    // ==================== Parameterized Status Tests ====================

    @ParameterizedTest(name = "status={0}, withdrawn={1}, deposited={2}")
    @MethodSource("statusConversionTestCases")
    fun `should handle status conversions correctly`(
        status: BankTransactionStatus,
        withdrawn: Boolean,
        deposited: Boolean,
    ) {
        // Given
        val id = UUID.randomUUID()
        val fromAccountId = UUID.randomUUID()
        val toAccountId = UUID.randomUUID()
        val now = Instant.now()
        val postgresBankTransaction =
            PostgresBankTransaction(
                id = 1L,
                aggregateId = id,
                correlationId = "corr-status-test",
                fromAccountId = fromAccountId,
                toAccountId = toAccountId,
                amount = BigDecimal("100.00"),
                moneyWithdrawn = withdrawn,
                moneyDeposited = deposited,
                status = status.name,
                version = 0L,
                createdAt = now,
                updatedAt = now,
            )

        // When
        val bankTransaction = postgresBankTransaction.toBankTransaction()

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
