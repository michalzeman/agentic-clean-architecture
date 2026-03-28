package mz.bank.account.adapter.nats.jetstream.mapper

import mz.bank.account.contract.proto.BankAccountEvent
import mz.bank.account.domain.Email
import mz.shared.domain.AggregateId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import mz.bank.account.domain.BankAccountEvent as DomainEvent

class BankAccountEventToJetStreamMapperTest {
    @Test
    fun `should map AccountCreated domain event to protobuf bytes`() {
        // Given
        val aggregateId = AggregateId("acc-123")
        val updatedAt = Instant.parse("2025-01-15T10:30:00Z")
        val email = Email("test@example.com")
        val initialBalance = BigDecimal("1000.50")

        val domainEvent =
            DomainEvent.AccountCreated(
                aggregateId = aggregateId,
                updatedAt = updatedAt,
                email = email,
                initialBalance = initialBalance,
            )

        // When
        val mapper = BankAccountEventToJetStreamMapper()
        val result = mapper.toProto(domainEvent)

        // Then
        val protoEvent = BankAccountEvent.parseFrom(result)
        assertThat(protoEvent.hasAccountCreated()).isTrue()
        assertThat(protoEvent.accountCreated.aggregateId).isEqualTo("acc-123")
        assertThat(protoEvent.accountCreated.email).isEqualTo("test@example.com")
    }

    @Test
    fun `should map MoneyDeposited domain event to protobuf bytes`() {
        // Given
        val aggregateId = AggregateId("acc-456")
        val updatedAt = Instant.parse("2025-01-15T11:00:00Z")
        val amount = BigDecimal("250.75")

        val domainEvent =
            DomainEvent.MoneyDeposited(
                aggregateId = aggregateId,
                updatedAt = updatedAt,
                amount = amount,
            )

        // When
        val mapper = BankAccountEventToJetStreamMapper()
        val result = mapper.toProto(domainEvent)

        // Then
        val protoEvent = BankAccountEvent.parseFrom(result)
        assertThat(protoEvent.hasMoneyDeposited()).isTrue()
        assertThat(protoEvent.moneyDeposited.amount).isEqualTo("250.75")
    }

    @Test
    fun `should map TransactionFinished domain event to protobuf bytes`() {
        // Given
        val aggregateId = AggregateId("acc-333")
        val updatedAt = Instant.parse("2025-01-15T15:00:00Z")
        val transactionId = "txn-003"

        val domainEvent =
            DomainEvent.TransactionFinished(
                aggregateId = aggregateId,
                updatedAt = updatedAt,
                transactionId = transactionId,
            )

        // When
        val mapper = BankAccountEventToJetStreamMapper()
        val result = mapper.toProto(domainEvent)

        // Then
        val protoEvent = BankAccountEvent.parseFrom(result)
        assertThat(protoEvent.hasTransactionFinished()).isTrue()
        assertThat(protoEvent.transactionFinished.transactionId).isEqualTo("txn-003")
    }
}
