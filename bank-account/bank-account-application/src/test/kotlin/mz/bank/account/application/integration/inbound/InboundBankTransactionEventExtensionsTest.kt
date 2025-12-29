package mz.bank.account.application.integration.inbound

import mz.bank.account.domain.BankAccountCommand
import mz.shared.domain.AggregateId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

class InboundBankTransactionEventExtensionsTest {
    @Test
    fun `should convert TransactionCreated to WithdrawForTransfer command`() {
        // Given
        val event =
            InboundBankTransactionEvent.TransactionCreated(
                aggregateId = AggregateId("tx-123"),
                correlationId = "corr-123",
                updatedAt = Instant.now(),
                fromAccountId = AggregateId("acc-from"),
                toAccountId = AggregateId("acc-to"),
                amount = BigDecimal("100.00"),
            )

        // When
        val command = event.toWithdrawForTransfer()

        // Then
        assertThat(command).isInstanceOf(BankAccountCommand.WithdrawForTransfer::class.java)
        assertThat(command.aggregateId).isEqualTo(AggregateId("acc-from"))
        assertThat(command.transactionId).isEqualTo("tx-123")
        assertThat(command.amount).isEqualByComparingTo(BigDecimal("100.00"))
    }

    @Test
    fun `should convert TransactionCreated to DepositFromTransfer command`() {
        // Given
        val event =
            InboundBankTransactionEvent.TransactionCreated(
                aggregateId = AggregateId("tx-456"),
                correlationId = "corr-456",
                updatedAt = Instant.now(),
                fromAccountId = AggregateId("acc-from"),
                toAccountId = AggregateId("acc-to"),
                amount = BigDecimal("200.00"),
            )

        // When
        val command = event.toDepositFromTransfer()

        // Then
        assertThat(command).isInstanceOf(BankAccountCommand.DepositFromTransfer::class.java)
        assertThat(command.aggregateId).isEqualTo(AggregateId("acc-to"))
        assertThat(command.transactionId).isEqualTo("tx-456")
        assertThat(command.amount).isEqualByComparingTo(BigDecimal("200.00"))
    }

    @Test
    fun `should convert TransactionRolledBack to DepositMoney command`() {
        // Given
        val event =
            InboundBankTransactionEvent.TransactionRolledBack(
                aggregateId = AggregateId("tx-789"),
                correlationId = "corr-789",
                updatedAt = Instant.now(),
                fromAccountId = AggregateId("acc-from"),
                toAccountId = AggregateId("acc-to"),
                amount = BigDecimal("150.00"),
            )

        // When
        val command = event.toDepositMoney()

        // Then
        assertThat(command).isInstanceOf(BankAccountCommand.DepositMoney::class.java)
        assertThat(command.aggregateId).isEqualTo(AggregateId("acc-from"))
        assertThat(command.amount).isEqualByComparingTo(BigDecimal("150.00"))
    }

    @Test
    fun `should convert TransactionRolledBack to WithdrawMoney command`() {
        // Given
        val event =
            InboundBankTransactionEvent.TransactionRolledBack(
                aggregateId = AggregateId("tx-012"),
                correlationId = "corr-012",
                updatedAt = Instant.now(),
                fromAccountId = AggregateId("acc-from"),
                toAccountId = AggregateId("acc-to"),
                amount = BigDecimal("300.00"),
            )

        // When
        val command = event.toWithdrawMoney()

        // Then
        assertThat(command).isInstanceOf(BankAccountCommand.WithdrawMoney::class.java)
        assertThat(command.aggregateId).isEqualTo(AggregateId("acc-to"))
        assertThat(command.amount).isEqualByComparingTo(BigDecimal("300.00"))
    }

    @Test
    fun `should convert TransactionWithdrawRolledBack to DepositMoney command`() {
        // Given
        val event =
            InboundBankTransactionEvent.TransactionWithdrawRolledBack(
                aggregateId = AggregateId("tx-345"),
                correlationId = "corr-345",
                updatedAt = Instant.now(),
                fromAccountId = AggregateId("acc-from"),
                amount = BigDecimal("500.00"),
            )

        // When
        val command = event.toDepositMoney()

        // Then
        assertThat(command).isInstanceOf(BankAccountCommand.DepositMoney::class.java)
        assertThat(command.aggregateId).isEqualTo(AggregateId("acc-from"))
        assertThat(command.amount).isEqualByComparingTo(BigDecimal("500.00"))
    }

    @Test
    fun `should convert TransactionDepositRolledBack to WithdrawMoney command`() {
        // Given
        val event =
            InboundBankTransactionEvent.TransactionDepositRolledBack(
                aggregateId = AggregateId("tx-678"),
                correlationId = "corr-678",
                updatedAt = Instant.now(),
                toAccountId = AggregateId("acc-to"),
                amount = BigDecimal("750.00"),
            )

        // When
        val command = event.toWithdrawMoney()

        // Then
        assertThat(command).isInstanceOf(BankAccountCommand.WithdrawMoney::class.java)
        assertThat(command.aggregateId).isEqualTo(AggregateId("acc-to"))
        assertThat(command.amount).isEqualByComparingTo(BigDecimal("750.00"))
    }
}
