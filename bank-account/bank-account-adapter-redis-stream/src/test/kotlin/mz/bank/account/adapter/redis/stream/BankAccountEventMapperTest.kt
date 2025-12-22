package mz.bank.account.adapter.redis.stream

import mz.bank.account.domain.BankAccountEvent
import mz.bank.account.domain.Email
import mz.shared.domain.AggregateId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

class BankAccountEventMapperTest {
    @Test
    fun `should map AccountCreated domain event to protobuf`() {
        // Given
        val aggregateId = AggregateId("acc-123")
        val updatedAt = Instant.parse("2025-01-15T10:30:00Z")
        val email = Email("test@example.com")
        val initialBalance = BigDecimal("1000.50")

        val domainEvent =
            BankAccountEvent.AccountCreated(
                aggregateId = aggregateId,
                updatedAt = updatedAt,
                email = email,
                initialBalance = initialBalance,
            )

        // When
        val protoEvent = BankAccountEventMapper.toProto(domainEvent)

        // Then
        assertThat(protoEvent.hasAccountCreated()).isTrue()
        assertThat(protoEvent.accountCreated.aggregateId).isEqualTo("acc-123")
        assertThat(protoEvent.accountCreated.updatedAtEpochMillis).isEqualTo(1736937000000L)
        assertThat(protoEvent.accountCreated.email).isEqualTo("test@example.com")
        assertThat(protoEvent.accountCreated.initialBalance).isEqualTo("1000.50")
    }

    @Test
    fun `should map MoneyDeposited domain event to protobuf`() {
        // Given
        val aggregateId = AggregateId("acc-456")
        val updatedAt = Instant.parse("2025-01-15T11:00:00Z")
        val amount = BigDecimal("250.75")

        val domainEvent =
            BankAccountEvent.MoneyDeposited(
                aggregateId = aggregateId,
                updatedAt = updatedAt,
                amount = amount,
            )

        // When
        val protoEvent = BankAccountEventMapper.toProto(domainEvent)

        // Then
        assertThat(protoEvent.hasMoneyDeposited()).isTrue()
        assertThat(protoEvent.moneyDeposited.aggregateId).isEqualTo("acc-456")
        assertThat(protoEvent.moneyDeposited.updatedAtEpochMillis).isEqualTo(1736938800000L)
        assertThat(protoEvent.moneyDeposited.amount).isEqualTo("250.75")
    }

    @Test
    fun `should map MoneyWithdrawn domain event to protobuf`() {
        // Given
        val aggregateId = AggregateId("acc-789")
        val updatedAt = Instant.parse("2025-01-15T12:00:00Z")
        val amount = BigDecimal("150.25")

        val domainEvent =
            BankAccountEvent.MoneyWithdrawn(
                aggregateId = aggregateId,
                updatedAt = updatedAt,
                amount = amount,
            )

        // When
        val protoEvent = BankAccountEventMapper.toProto(domainEvent)

        // Then
        assertThat(protoEvent.hasMoneyWithdrawn()).isTrue()
        assertThat(protoEvent.moneyWithdrawn.aggregateId).isEqualTo("acc-789")
        assertThat(protoEvent.moneyWithdrawn.updatedAtEpochMillis).isEqualTo(1736942400000L)
        assertThat(protoEvent.moneyWithdrawn.amount).isEqualTo("150.25")
    }

    @Test
    fun `should map TransferWithdrawalStarted domain event to protobuf`() {
        // Given
        val aggregateId = AggregateId("acc-111")
        val updatedAt = Instant.parse("2025-01-15T13:00:00Z")
        val transactionId = "txn-001"
        val amount = BigDecimal("500.00")

        val domainEvent =
            BankAccountEvent.TransferWithdrawalStarted(
                aggregateId = aggregateId,
                updatedAt = updatedAt,
                transactionId = transactionId,
                amount = amount,
            )

        // When
        val protoEvent = BankAccountEventMapper.toProto(domainEvent)

        // Then
        assertThat(protoEvent.hasTransferWithdrawalStarted()).isTrue()
        assertThat(protoEvent.transferWithdrawalStarted.aggregateId).isEqualTo("acc-111")
        assertThat(protoEvent.transferWithdrawalStarted.updatedAtEpochMillis).isEqualTo(1736946000000L)
        assertThat(protoEvent.transferWithdrawalStarted.transactionId).isEqualTo("txn-001")
        assertThat(protoEvent.transferWithdrawalStarted.amount).isEqualTo("500.00")
    }

    @Test
    fun `should map TransferDepositStarted domain event to protobuf`() {
        // Given
        val aggregateId = AggregateId("acc-222")
        val updatedAt = Instant.parse("2025-01-15T14:00:00Z")
        val transactionId = "txn-002"
        val amount = BigDecimal("750.99")

        val domainEvent =
            BankAccountEvent.TransferDepositStarted(
                aggregateId = aggregateId,
                updatedAt = updatedAt,
                transactionId = transactionId,
                amount = amount,
            )

        // When
        val protoEvent = BankAccountEventMapper.toProto(domainEvent)

        // Then
        assertThat(protoEvent.hasTransferDepositStarted()).isTrue()
        assertThat(protoEvent.transferDepositStarted.aggregateId).isEqualTo("acc-222")
        assertThat(protoEvent.transferDepositStarted.updatedAtEpochMillis).isEqualTo(1736949600000L)
        assertThat(protoEvent.transferDepositStarted.transactionId).isEqualTo("txn-002")
        assertThat(protoEvent.transferDepositStarted.amount).isEqualTo("750.99")
    }

    @Test
    fun `should map TransactionFinished domain event to protobuf`() {
        // Given
        val aggregateId = AggregateId("acc-333")
        val updatedAt = Instant.parse("2025-01-15T15:00:00Z")
        val transactionId = "txn-003"

        val domainEvent =
            BankAccountEvent.TransactionFinished(
                aggregateId = aggregateId,
                updatedAt = updatedAt,
                transactionId = transactionId,
            )

        // When
        val protoEvent = BankAccountEventMapper.toProto(domainEvent)

        // Then
        assertThat(protoEvent.hasTransactionFinished()).isTrue()
        assertThat(protoEvent.transactionFinished.aggregateId).isEqualTo("acc-333")
        assertThat(protoEvent.transactionFinished.updatedAtEpochMillis).isEqualTo(1736953200000L)
        assertThat(protoEvent.transactionFinished.transactionId).isEqualTo("txn-003")
    }
}
