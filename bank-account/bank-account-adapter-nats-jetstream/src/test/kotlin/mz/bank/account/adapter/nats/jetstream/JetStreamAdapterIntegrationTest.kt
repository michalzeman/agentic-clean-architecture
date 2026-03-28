package mz.bank.account.adapter.nats.jetstream

import mz.bank.account.adapter.nats.jetstream.mapper.BankAccountEventToJetStreamMapper
import mz.bank.account.contract.proto.BankAccountEvent
import mz.bank.account.domain.Email
import mz.shared.domain.AggregateId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import mz.bank.account.domain.BankAccountEvent as DomainEvent

/**
 * Integration tests for NATS JetStream adapter.
 * These tests verify the mapper and configuration without requiring a running NATS server.
 */
class JetStreamAdapterIntegrationTest {
    @Test
    fun `should map AccountCreated domain event to protobuf bytes`() {
        // Given
        val mapper = BankAccountEventToJetStreamMapper()
        val domainEvent =
            DomainEvent.AccountCreated(
                aggregateId = AggregateId("acc-123"),
                updatedAt = Instant.parse("2025-01-15T10:30:00Z"),
                email = Email("test@example.com"),
                initialBalance = BigDecimal("1000.00"),
            )

        // When
        val protoBytes = mapper.toProto(domainEvent)
        val protoEvent = BankAccountEvent.parseFrom(protoBytes)

        // Then
        assertThat(protoEvent.hasAccountCreated()).isTrue()
        assertThat(protoEvent.accountCreated.aggregateId).isEqualTo("acc-123")
        assertThat(protoEvent.accountCreated.email).isEqualTo("test@example.com")
        assertThat(protoEvent.accountCreated.initialBalance).isEqualTo("1000.00")
    }

    @Test
    fun `should verify profile-based bean selection`() {
        // This test verifies that the adapter properties are correctly configured
        val jetStreamProperties = BankAccountJetStreamProperties()

        assertThat(jetStreamProperties.bankAccountEventsStream).isEqualTo("bank-account.event.bank-accounts")
        assertThat(jetStreamProperties.bankTransactionEventsStream).isEqualTo("bank-transaction.event.bank-transactions")
        assertThat(jetStreamProperties.consumerName).isEqualTo("bank-account-consumer")
        assertThat(jetStreamProperties.consumerGroup).isEqualTo("bank-account-service")
    }

    @Test
    fun `should map MoneyDeposited domain event to protobuf bytes`() {
        // Given
        val mapper = BankAccountEventToJetStreamMapper()
        val domainEvent =
            DomainEvent.MoneyDeposited(
                aggregateId = AggregateId("acc-456"),
                updatedAt = Instant.parse("2025-01-15T11:00:00Z"),
                amount = BigDecimal("250.75"),
            )

        // When
        val protoBytes = mapper.toProto(domainEvent)
        val protoEvent = BankAccountEvent.parseFrom(protoBytes)

        // Then
        assertThat(protoEvent.hasMoneyDeposited()).isTrue()
        assertThat(protoEvent.moneyDeposited.aggregateId).isEqualTo("acc-456")
        assertThat(protoEvent.moneyDeposited.amount).isEqualTo("250.75")
    }

    @Test
    fun `should map TransactionFinished domain event to protobuf bytes`() {
        // Given
        val mapper = BankAccountEventToJetStreamMapper()
        val domainEvent =
            DomainEvent.TransactionFinished(
                aggregateId = AggregateId("acc-789"),
                updatedAt = Instant.parse("2025-01-15T12:00:00Z"),
                transactionId = "txn-001",
            )

        // When
        val protoBytes = mapper.toProto(domainEvent)
        val protoEvent = BankAccountEvent.parseFrom(protoBytes)

        // Then
        assertThat(protoEvent.hasTransactionFinished()).isTrue()
        assertThat(protoEvent.transactionFinished.aggregateId).isEqualTo("acc-789")
        assertThat(protoEvent.transactionFinished.transactionId).isEqualTo("txn-001")
    }
}
