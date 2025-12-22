package mz.bank.transaction.adapter.redis.stream

import mz.bank.transaction.domain.BankTransactionEvent
import mz.shared.domain.AggregateId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

class BankTransactionEventMapperTest {
    @Test
    fun `should map BankTransactionCreated domain event to protobuf`() {
        // Given
        val aggregateId = AggregateId("txn-123")
        val correlationId = "corr-001"
        val updatedAt = Instant.parse("2025-01-15T10:30:00Z")
        val fromAccountId = AggregateId("acc-from-001")
        val toAccountId = AggregateId("acc-to-001")
        val amount = BigDecimal("1000.50")

        val domainEvent =
            BankTransactionEvent.BankTransactionCreated(
                aggregateId = aggregateId,
                correlationId = correlationId,
                updatedAt = updatedAt,
                fromAccountId = fromAccountId,
                toAccountId = toAccountId,
                amount = amount,
            )

        // When
        val protoEvent = BankTransactionEventMapper.toProto(domainEvent)

        // Then
        assertThat(protoEvent.hasTransactionCreated()).isTrue()
        assertThat(protoEvent.transactionCreated.aggregateId).isEqualTo("txn-123")
        assertThat(protoEvent.transactionCreated.correlationId).isEqualTo("corr-001")
        assertThat(protoEvent.transactionCreated.updatedAtEpochMillis).isEqualTo(1736937000000L)
        assertThat(protoEvent.transactionCreated.fromAccountId).isEqualTo("acc-from-001")
        assertThat(protoEvent.transactionCreated.toAccountId).isEqualTo("acc-to-001")
        assertThat(protoEvent.transactionCreated.amount).isEqualTo("1000.50")
    }

    @Test
    fun `should map BankTransactionMoneyWithdrawn domain event to protobuf`() {
        // Given
        val aggregateId = AggregateId("txn-456")
        val correlationId = "corr-002"
        val updatedAt = Instant.parse("2025-01-15T11:00:00Z")
        val accountId = AggregateId("acc-from-002")

        val domainEvent =
            BankTransactionEvent.BankTransactionMoneyWithdrawn(
                aggregateId = aggregateId,
                correlationId = correlationId,
                updatedAt = updatedAt,
                accountId = accountId,
            )

        // When
        val protoEvent = BankTransactionEventMapper.toProto(domainEvent)

        // Then
        assertThat(protoEvent.hasTransactionMoneyWithdrawn()).isTrue()
        assertThat(protoEvent.transactionMoneyWithdrawn.aggregateId).isEqualTo("txn-456")
        assertThat(protoEvent.transactionMoneyWithdrawn.correlationId).isEqualTo("corr-002")
        assertThat(protoEvent.transactionMoneyWithdrawn.updatedAtEpochMillis).isEqualTo(1736938800000L)
        assertThat(protoEvent.transactionMoneyWithdrawn.accountId).isEqualTo("acc-from-002")
    }

    @Test
    fun `should map BankTransactionMoneyDeposited domain event to protobuf`() {
        // Given
        val aggregateId = AggregateId("txn-789")
        val correlationId = "corr-003"
        val updatedAt = Instant.parse("2025-01-15T12:00:00Z")
        val accountId = AggregateId("acc-to-003")

        val domainEvent =
            BankTransactionEvent.BankTransactionMoneyDeposited(
                aggregateId = aggregateId,
                correlationId = correlationId,
                updatedAt = updatedAt,
                accountId = accountId,
            )

        // When
        val protoEvent = BankTransactionEventMapper.toProto(domainEvent)

        // Then
        assertThat(protoEvent.hasTransactionMoneyDeposited()).isTrue()
        assertThat(protoEvent.transactionMoneyDeposited.aggregateId).isEqualTo("txn-789")
        assertThat(protoEvent.transactionMoneyDeposited.correlationId).isEqualTo("corr-003")
        assertThat(protoEvent.transactionMoneyDeposited.updatedAtEpochMillis).isEqualTo(1736942400000L)
        assertThat(protoEvent.transactionMoneyDeposited.accountId).isEqualTo("acc-to-003")
    }

    @Test
    fun `should map BankTransactionFinished domain event to protobuf`() {
        // Given
        val aggregateId = AggregateId("txn-111")
        val correlationId = "corr-004"
        val updatedAt = Instant.parse("2025-01-15T13:00:00Z")
        val fromAccountId = AggregateId("acc-from-004")
        val toAccountId = AggregateId("acc-to-004")

        val domainEvent =
            BankTransactionEvent.BankTransactionFinished(
                aggregateId = aggregateId,
                correlationId = correlationId,
                updatedAt = updatedAt,
                fromAccountId = fromAccountId,
                toAccountId = toAccountId,
            )

        // When
        val protoEvent = BankTransactionEventMapper.toProto(domainEvent)

        // Then
        assertThat(protoEvent.hasTransactionFinished()).isTrue()
        assertThat(protoEvent.transactionFinished.aggregateId).isEqualTo("txn-111")
        assertThat(protoEvent.transactionFinished.correlationId).isEqualTo("corr-004")
        assertThat(protoEvent.transactionFinished.updatedAtEpochMillis).isEqualTo(1736946000000L)
        assertThat(protoEvent.transactionFinished.fromAccountId).isEqualTo("acc-from-004")
        assertThat(protoEvent.transactionFinished.toAccountId).isEqualTo("acc-to-004")
    }

    @Test
    fun `should map BankTransactionFailed domain event to protobuf`() {
        // Given
        val aggregateId = AggregateId("txn-222")
        val correlationId = "corr-005"
        val updatedAt = Instant.parse("2025-01-15T14:00:00Z")
        val reason = "Insufficient funds"

        val domainEvent =
            BankTransactionEvent.BankTransactionFailed(
                aggregateId = aggregateId,
                correlationId = correlationId,
                updatedAt = updatedAt,
                reason = reason,
            )

        // When
        val protoEvent = BankTransactionEventMapper.toProto(domainEvent)

        // Then
        assertThat(protoEvent.hasTransactionFailed()).isTrue()
        assertThat(protoEvent.transactionFailed.aggregateId).isEqualTo("txn-222")
        assertThat(protoEvent.transactionFailed.correlationId).isEqualTo("corr-005")
        assertThat(protoEvent.transactionFailed.updatedAtEpochMillis).isEqualTo(1736949600000L)
        assertThat(protoEvent.transactionFailed.reason).isEqualTo("Insufficient funds")
    }

    @Test
    fun `should map BankTransactionRolledBack domain event to protobuf`() {
        // Given
        val aggregateId = AggregateId("txn-333")
        val correlationId = "corr-006"
        val updatedAt = Instant.parse("2025-01-15T15:00:00Z")
        val fromAccountId = AggregateId("acc-from-006")
        val toAccountId = AggregateId("acc-to-006")
        val amount = BigDecimal("500.25")

        val domainEvent =
            BankTransactionEvent.BankTransactionRolledBack(
                aggregateId = aggregateId,
                correlationId = correlationId,
                updatedAt = updatedAt,
                fromAccountId = fromAccountId,
                toAccountId = toAccountId,
                amount = amount,
            )

        // When
        val protoEvent = BankTransactionEventMapper.toProto(domainEvent)

        // Then
        assertThat(protoEvent.hasTransactionRolledBack()).isTrue()
        assertThat(protoEvent.transactionRolledBack.aggregateId).isEqualTo("txn-333")
        assertThat(protoEvent.transactionRolledBack.correlationId).isEqualTo("corr-006")
        assertThat(protoEvent.transactionRolledBack.updatedAtEpochMillis).isEqualTo(1736953200000L)
        assertThat(protoEvent.transactionRolledBack.fromAccountId).isEqualTo("acc-from-006")
        assertThat(protoEvent.transactionRolledBack.toAccountId).isEqualTo("acc-to-006")
        assertThat(protoEvent.transactionRolledBack.amount).isEqualTo("500.25")
    }

    @Test
    fun `should map BankTransactionWithdrawRolledBack domain event to protobuf`() {
        // Given
        val aggregateId = AggregateId("txn-444")
        val correlationId = "corr-007"
        val updatedAt = Instant.parse("2025-01-15T16:00:00Z")

        val domainEvent =
            BankTransactionEvent.BankTransactionWithdrawRolledBack(
                aggregateId = aggregateId,
                correlationId = correlationId,
                updatedAt = updatedAt,
            )

        // When
        val protoEvent = BankTransactionEventMapper.toProto(domainEvent)

        // Then
        assertThat(protoEvent.hasTransactionWithdrawRolledBack()).isTrue()
        assertThat(protoEvent.transactionWithdrawRolledBack.aggregateId).isEqualTo("txn-444")
        assertThat(protoEvent.transactionWithdrawRolledBack.correlationId).isEqualTo("corr-007")
        assertThat(protoEvent.transactionWithdrawRolledBack.updatedAtEpochMillis).isEqualTo(1736956800000L)
    }

    @Test
    fun `should map BankTransactionDepositRolledBack domain event to protobuf`() {
        // Given
        val aggregateId = AggregateId("txn-555")
        val correlationId = "corr-008"
        val updatedAt = Instant.parse("2025-01-15T17:00:00Z")

        val domainEvent =
            BankTransactionEvent.BankTransactionDepositRolledBack(
                aggregateId = aggregateId,
                correlationId = correlationId,
                updatedAt = updatedAt,
            )

        // When
        val protoEvent = BankTransactionEventMapper.toProto(domainEvent)

        // Then
        assertThat(protoEvent.hasTransactionDepositRolledBack()).isTrue()
        assertThat(protoEvent.transactionDepositRolledBack.aggregateId).isEqualTo("txn-555")
        assertThat(protoEvent.transactionDepositRolledBack.correlationId).isEqualTo("corr-008")
        assertThat(protoEvent.transactionDepositRolledBack.updatedAtEpochMillis).isEqualTo(1736960400000L)
    }
}
