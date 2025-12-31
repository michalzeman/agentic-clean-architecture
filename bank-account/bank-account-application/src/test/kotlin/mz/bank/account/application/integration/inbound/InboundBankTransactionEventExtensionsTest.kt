package mz.bank.account.application.integration.inbound

import mz.bank.account.application.transaction.InboundBankTransactionEvent
import mz.bank.account.application.transaction.toCommand
import mz.bank.account.application.transaction.toDepositFromTransfer
import mz.bank.account.application.transaction.toFinishTransaction
import mz.bank.account.application.transaction.toRollbackDepositFromTransfer
import mz.bank.account.application.transaction.toRollbackWithdrawForTransfer
import mz.bank.account.application.transaction.toWithdrawForTransfer
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
    fun `should convert TransactionWithdrawRolledBack to RollbackWithdrawForTransfer command`() {
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
        val command = event.toRollbackWithdrawForTransfer()

        // Then
        assertThat(command).isInstanceOf(BankAccountCommand.RollbackWithdrawForTransfer::class.java)
        assertThat(command.aggregateId).isEqualTo(AggregateId("acc-from"))
        assertThat(command.transactionId).isEqualTo("tx-345")
        assertThat(command.amount).isEqualByComparingTo(BigDecimal("500.00"))
    }

    @Test
    fun `should convert TransactionDepositRolledBack to RollbackDepositFromTransfer command`() {
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
        val command = event.toRollbackDepositFromTransfer()

        // Then
        assertThat(command).isInstanceOf(BankAccountCommand.RollbackDepositFromTransfer::class.java)
        assertThat(command.aggregateId).isEqualTo(AggregateId("acc-to"))
        assertThat(command.transactionId).isEqualTo("tx-678")
        assertThat(command.amount).isEqualByComparingTo(BigDecimal("750.00"))
    }

    @Test
    fun `should convert TransactionMoneyWithdrawn to DepositFromTransfer command`() {
        // Given
        val event =
            InboundBankTransactionEvent.TransactionMoneyWithdrawn(
                aggregateId = AggregateId("tx-999"),
                correlationId = "corr-999",
                updatedAt = Instant.now(),
                accountId = AggregateId("acc-from"),
                toAccountId = AggregateId("acc-to"),
                amount = BigDecimal("300.00"),
            )

        // When
        val command = event.toDepositFromTransfer()

        // Then
        assertThat(command).isInstanceOf(BankAccountCommand.DepositFromTransfer::class.java)
        assertThat(command.aggregateId).isEqualTo(AggregateId("acc-to"))
        assertThat(command.transactionId).isEqualTo("tx-999")
        assertThat(command.amount).isEqualByComparingTo(BigDecimal("300.00"))
    }

    @Test
    fun `should convert TransactionMoneyDeposited to FinishTransaction command`() {
        // Given
        val event =
            InboundBankTransactionEvent.TransactionMoneyDeposited(
                aggregateId = AggregateId("tx-888"),
                correlationId = "corr-888",
                updatedAt = Instant.now(),
                accountId = AggregateId("acc-to"),
            )

        // When
        val command = event.toFinishTransaction()

        // Then
        assertThat(command).isInstanceOf(BankAccountCommand.FinishTransaction::class.java)
        assertThat(command.aggregateId).isEqualTo(AggregateId("acc-to"))
        assertThat(command.transactionId).isEqualTo("tx-888")
    }

    @Test
    fun `should route TransactionCreated through toCommand`() {
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
        val command = event.toCommand()

        // Then
        assertThat(command).isInstanceOf(BankAccountCommand.WithdrawForTransfer::class.java)
    }

    @Test
    fun `should route TransactionMoneyWithdrawn through toCommand`() {
        // Given
        val event =
            InboundBankTransactionEvent.TransactionMoneyWithdrawn(
                aggregateId = AggregateId("tx-456"),
                correlationId = "corr-456",
                updatedAt = Instant.now(),
                accountId = AggregateId("acc-from"),
                toAccountId = AggregateId("acc-to"),
                amount = BigDecimal("200.00"),
            )

        // When
        val command = event.toCommand()

        // Then
        assertThat(command).isInstanceOf(BankAccountCommand.DepositFromTransfer::class.java)
    }

    @Test
    fun `should route TransactionMoneyDeposited through toCommand`() {
        // Given
        val event =
            InboundBankTransactionEvent.TransactionMoneyDeposited(
                aggregateId = AggregateId("tx-789"),
                correlationId = "corr-789",
                updatedAt = Instant.now(),
                accountId = AggregateId("acc-to"),
            )

        // When
        val command = event.toCommand()

        // Then
        assertThat(command).isInstanceOf(BankAccountCommand.FinishTransaction::class.java)
    }

    @Test
    fun `should return FinishTransactions for unsupported events through toCommand`() {
        // Given
        val event =
            InboundBankTransactionEvent.TransactionFinished(
                aggregateId = AggregateId("tx-completed"),
                correlationId = "corr-completed",
                updatedAt = Instant.now(),
                fromAccountId = AggregateId("acc-from"),
                toAccountId = AggregateId("acc-to"),
            )

        // When
        val command = event.toCommand()

        // Then
        assertThat(command).isInstanceOf(BankAccountCommand.FinishTransactions::class.java)
        assertThat((command as BankAccountCommand.FinishTransactions).commands)
            .hasSize(2)
            .anySatisfy {
                assertThat(it.aggregateId).isEqualTo(AggregateId("acc-from"))
                assertThat(it.transactionId).isEqualTo("tx-completed")
            }.anySatisfy {
                assertThat(it.aggregateId).isEqualTo(AggregateId("acc-to"))
                assertThat(it.transactionId).isEqualTo("tx-completed")
            }
    }
}
