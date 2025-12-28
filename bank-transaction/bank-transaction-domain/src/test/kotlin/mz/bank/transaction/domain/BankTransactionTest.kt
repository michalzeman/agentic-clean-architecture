package mz.bank.transaction.domain

import mz.shared.domain.AggregateId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

class BankTransactionTest {
    @Test
    fun `should create valid BankTransaction`() {
        // Given
        val now = Instant.now()
        val aggregateId = AggregateId("txn-001")
        val fromAccountId = AggregateId("acc-from")
        val toAccountId = AggregateId("acc-to")
        val amount = BigDecimal("100.00")

        // When
        val transaction =
            BankTransaction(
                aggregateId = aggregateId,
                correlationId = "corr-001",
                fromAccountId = fromAccountId,
                toAccountId = toAccountId,
                amount = amount,
                status = BankTransactionStatus.INITIALIZED,
                createdAt = now,
                updatedAt = now,
            )

        // Then
        assertThat(transaction.aggregateId).isEqualTo(aggregateId)
        assertThat(transaction.correlationId).isEqualTo("corr-001")
        assertThat(transaction.fromAccountId).isEqualTo(fromAccountId)
        assertThat(transaction.toAccountId).isEqualTo(toAccountId)
        assertThat(transaction.amount).isEqualByComparingTo(amount)
        assertThat(transaction.status).isEqualTo(BankTransactionStatus.INITIALIZED)
    }

    @Test
    fun `should fail to create BankTransaction with negative amount`() {
        // When & Then
        assertThatThrownBy {
            BankTransaction(
                aggregateId = AggregateId("txn-001"),
                correlationId = "corr-001",
                fromAccountId = AggregateId("acc-from"),
                toAccountId = AggregateId("acc-to"),
                amount = BigDecimal("-100.00"),
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("BankTransaction amount must be positive")
    }

    @Test
    fun `should fail to create BankTransaction with zero amount`() {
        // When & Then
        assertThatThrownBy {
            BankTransaction(
                aggregateId = AggregateId("txn-001"),
                correlationId = "corr-001",
                fromAccountId = AggregateId("acc-from"),
                toAccountId = AggregateId("acc-to"),
                amount = BigDecimal.ZERO,
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("BankTransaction amount must be positive")
    }

    @Test
    fun `should fail to create BankTransaction with same source and destination accounts`() {
        // When & Then
        assertThatThrownBy {
            BankTransaction(
                aggregateId = AggregateId("txn-001"),
                correlationId = "corr-001",
                fromAccountId = AggregateId("acc-same"),
                toAccountId = AggregateId("acc-same"),
                amount = BigDecimal("100.00"),
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Source and destination accounts must be different")
    }

    @Test
    fun `should fail to create BankTransaction with blank correlation ID`() {
        // When & Then
        assertThatThrownBy {
            BankTransaction(
                aggregateId = AggregateId("txn-001"),
                correlationId = "",
                fromAccountId = AggregateId("acc-from"),
                toAccountId = AggregateId("acc-to"),
                amount = BigDecimal("100.00"),
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Correlation ID cannot be blank")
    }

    @Test
    fun `should fail to create FINISHED transaction without both operations completed`() {
        // When & Then
        assertThatThrownBy {
            BankTransaction(
                aggregateId = AggregateId("txn-001"),
                correlationId = "corr-001",
                fromAccountId = AggregateId("acc-from"),
                toAccountId = AggregateId("acc-to"),
                amount = BigDecimal("100.00"),
                status = BankTransactionStatus.FINISHED,
                moneyWithdrawn = true,
                moneyDeposited = false,
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("FINISHED status requires both withdraw and deposit completed")
    }

    @Test
    fun `should fail to create INITIALIZED transaction with operations completed`() {
        // When & Then
        assertThatThrownBy {
            BankTransaction(
                aggregateId = AggregateId("txn-001"),
                correlationId = "corr-001",
                fromAccountId = AggregateId("acc-from"),
                toAccountId = AggregateId("acc-to"),
                amount = BigDecimal("100.00"),
                status = BankTransactionStatus.INITIALIZED,
                moneyWithdrawn = true,
                moneyDeposited = false,
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("INITIALIZED status requires no operations completed")
    }
}
