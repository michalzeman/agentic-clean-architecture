package mz.bank.transaction.application.account

import kotlinx.coroutines.runBlocking
import mz.bank.transaction.application.transaction.BankTransactionRepository
import mz.bank.transaction.domain.BankTransaction
import mz.bank.transaction.domain.BankTransactionCommand
import mz.bank.transaction.domain.BankTransactionStatus
import mz.shared.domain.AggregateId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Instant

class AccountEventExtensionsTest {
    @Test
    fun `should convert TransferWithdrawalStartedEvent to ValidateWithdrawCommand`() {
        // Given
        val event =
            AccountEvent.TransferWithdrawalStartedEvent(
                accountId = AggregateId("acc-from"),
                transactionId = "tx-123",
                amount = BigDecimal("100.00"),
            )

        // When
        val command = event.toValidateWithdrawCommand()

        // Then
        assertThat(command).isInstanceOf(BankTransactionCommand.ValidateBankTransactionMoneyWithdraw::class.java)
        assertThat(command.aggregateId).isEqualTo(AggregateId("tx-123"))
        assertThat(command.accountId).isEqualTo(AggregateId("acc-from"))
        assertThat(command.correlationId).isEqualTo("tx-123")
    }

    @Test
    fun `should convert TransferDepositStartedEvent to ValidateDepositCommand`() {
        // Given
        val event =
            AccountEvent.TransferDepositStartedEvent(
                accountId = AggregateId("acc-to"),
                transactionId = "tx-456",
                amount = BigDecimal("200.00"),
            )

        // When
        val command = event.toValidateDepositCommand()

        // Then
        assertThat(command).isInstanceOf(BankTransactionCommand.ValidateBankTransactionMoneyDeposit::class.java)
        assertThat(command.aggregateId).isEqualTo(AggregateId("tx-456"))
        assertThat(command.accountId).isEqualTo(AggregateId("acc-to"))
        assertThat(command.correlationId).isEqualTo("tx-456")
    }

    @Test
    fun `should convert TransactionFinishedEvent to FinishCommand with repository lookup`(): Unit =
        runBlocking {
            // Given
            val repository: BankTransactionRepository = mock()
            val event =
                AccountEvent.TransactionFinishedEvent(
                    accountId = AggregateId("acc-999"),
                    transactionId = "tx-789",
                )

            val transaction =
                BankTransaction(
                    aggregateId = AggregateId("tx-789"),
                    version = 3,
                    correlationId = "corr-abc",
                    fromAccountId = AggregateId("acc-from"),
                    toAccountId = AggregateId("acc-to"),
                    amount = BigDecimal("300.00"),
                    status = BankTransactionStatus.CREATED,
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                )

            whenever(repository.findById(AggregateId("tx-789"))).thenReturn(transaction)

            // When
            val command = event.toFinishCommand(repository)

            // Then
            assertThat(command).isNotNull
            assertThat(command).isInstanceOf(BankTransactionCommand.FinishBankTransaction::class.java)
            assertThat(command!!.aggregateId).isEqualTo(AggregateId("tx-789"))
            assertThat(command.correlationId).isEqualTo("corr-abc")
            assertThat(command.fromAccountId).isEqualTo(AggregateId("acc-from"))
            assertThat(command.toAccountId).isEqualTo(AggregateId("acc-to"))
        }

    @Test
    fun `should return null when transaction not found for FinishCommand`(): Unit =
        runBlocking {
            // Given
            val repository: BankTransactionRepository = mock()
            val event =
                AccountEvent.TransactionFinishedEvent(
                    accountId = AggregateId("acc-999"),
                    transactionId = "tx-unknown",
                )

            whenever(repository.findById(AggregateId("tx-unknown"))).thenReturn(null)

            // When
            val command = event.toFinishCommand(repository)

            // Then
            assertThat(command).isNull()
        }

    @Test
    fun `should route TransferWithdrawalStartedEvent through toCommand`(): Unit =
        runBlocking {
            // Given
            val repository: BankTransactionRepository = mock()
            val event =
                AccountEvent.TransferWithdrawalStartedEvent(
                    accountId = AggregateId("acc-from"),
                    transactionId = "tx-123",
                    amount = BigDecimal("100.00"),
                )

            // When
            val command = event.toCommand(repository)

            // Then
            assertThat(command).isNotNull
            assertThat(command).isInstanceOf(BankTransactionCommand.ValidateBankTransactionMoneyWithdraw::class.java)
        }

    @Test
    fun `should route TransferDepositStartedEvent through toCommand`(): Unit =
        runBlocking {
            // Given
            val repository: BankTransactionRepository = mock()
            val event =
                AccountEvent.TransferDepositStartedEvent(
                    accountId = AggregateId("acc-to"),
                    transactionId = "tx-456",
                    amount = BigDecimal("200.00"),
                )

            // When
            val command = event.toCommand(repository)

            // Then
            assertThat(command).isNotNull
            assertThat(command).isInstanceOf(BankTransactionCommand.ValidateBankTransactionMoneyDeposit::class.java)
        }

    @Test
    fun `should return null for AccountCreatedEvent through toCommand`(): Unit =
        runBlocking {
            // Given
            val repository: BankTransactionRepository = mock()
            val event = AccountEvent.AccountCreatedEvent(accountId = AggregateId("acc-123"))

            // When
            val command = event.toCommand(repository)

            // Then
            assertThat(command).isNull()
        }

    @Test
    fun `should return null for DefaultAccountEvent through toCommand`(): Unit =
        runBlocking {
            // Given
            val repository: BankTransactionRepository = mock()
            val event = AccountEvent.DefaultAccountEvent

            // When
            val command = event.toCommand(repository)

            // Then
            assertThat(command).isNull()
        }

    @Test
    fun `should return null for TransferWithdrawalRolledBackEvent through toCommand`(): Unit =
        runBlocking {
            // Given
            val repository: BankTransactionRepository = mock()
            val event =
                AccountEvent.TransferWithdrawalRolledBackEvent(
                    accountId = AggregateId("acc-from"),
                    transactionId = "tx-rollback",
                    amount = BigDecimal("50.00"),
                )

            // When
            val command = event.toCommand(repository)

            // Then
            assertThat(command).isNull()
        }

    @Test
    fun `should return null for TransferDepositRolledBackEvent through toCommand`(): Unit =
        runBlocking {
            // Given
            val repository: BankTransactionRepository = mock()
            val event =
                AccountEvent.TransferDepositRolledBackEvent(
                    accountId = AggregateId("acc-to"),
                    transactionId = "tx-rollback",
                    amount = BigDecimal("50.00"),
                )

            // When
            val command = event.toCommand(repository)

            // Then
            assertThat(command).isNull()
        }
}
