package mz.bank.transaction.application

import kotlinx.coroutines.runBlocking
import mz.bank.transaction.domain.BankTransaction
import mz.bank.transaction.domain.BankTransactionCommand
import mz.bank.transaction.domain.BankTransactionStatus
import mz.shared.domain.AggregateId
import mz.shared.domain.LockProvider
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Instant

class BankTransactionCommandHandlerTest {
    private lateinit var bankTransactionRepository: BankTransactionRepository
    private lateinit var lockProvider: LockProvider
    private lateinit var commandHandler: BankTransactionCommandHandler

    @BeforeEach
    fun setUp() {
        bankTransactionRepository = mock()
        lockProvider = FakeLockProvider()
        commandHandler = BankTransactionCommandHandler(bankTransactionRepository, lockProvider)
    }

    // ==================== CreateBankTransaction Tests ====================

    @Test
    fun `should create bankTransaction successfully`(): Unit =
        runBlocking {
            // Given
            val command =
                BankTransactionCommand.CreateBankTransaction(
                    correlationId = "corr-001",
                    fromAccountId = AggregateId("acc-001"),
                    toAccountId = AggregateId("acc-002"),
                    amount = BigDecimal("100.00"),
                )

            whenever(bankTransactionRepository.upsert(any())).thenAnswer { invocation ->
                val aggregate = invocation.getArgument<mz.bank.transaction.domain.BankTransactionAggregate>(0)
                aggregate.bankTransaction
            }

            // When
            val result = commandHandler.handle(command)

            // Then
            assertThat(result.aggregateId.value).isNotBlank()
            assertThat(result.correlationId).isEqualTo("corr-001")
            assertThat(result.fromAccountId).isEqualTo(AggregateId("acc-001"))
            assertThat(result.toAccountId).isEqualTo(AggregateId("acc-002"))
            assertThat(result.amount).isEqualByComparingTo(BigDecimal("100.00"))
            assertThat(result.status).isEqualTo(BankTransactionStatus.CREATED)
            verify(bankTransactionRepository).upsert(any())
        }

    @Test
    fun `should throw exception when creating bankTransaction with zero amount`(): Unit =
        runBlocking {
            // Given
            val command =
                BankTransactionCommand.CreateBankTransaction(
                    correlationId = "corr-002",
                    fromAccountId = AggregateId("acc-001"),
                    toAccountId = AggregateId("acc-002"),
                    amount = BigDecimal.ZERO,
                )

            // When & Then
            assertThatThrownBy { runBlocking { commandHandler.handle(command) } }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("amount must be positive")
        }

    // ==================== ValidateBankTransactionMoneyWithdraw Tests ====================

    @Test
    fun `should validate money withdraw successfully`(): Unit =
        runBlocking {
            // Given
            val aggregateId = AggregateId("txn-001")
            val existingTransaction =
                createBankTransaction(
                    aggregateId = aggregateId,
                    status = BankTransactionStatus.CREATED,
                )
            val command =
                BankTransactionCommand.ValidateBankTransactionMoneyWithdraw(
                    aggregateId = aggregateId,
                    correlationId = "corr-003",
                )
            val expectedTransaction =
                createBankTransaction(
                    aggregateId = aggregateId,
                    moneyWithdrawn = true,
                    status = BankTransactionStatus.CREATED,
                    version = 1L,
                )

            whenever(bankTransactionRepository.findById(aggregateId)).thenReturn(existingTransaction)
            whenever(bankTransactionRepository.upsert(any())).thenReturn(expectedTransaction)

            // When
            val result = commandHandler.handle(command)

            // Then
            assertThat(result.moneyWithdrawn).isTrue()
            verify(bankTransactionRepository).findById(aggregateId)
            verify(bankTransactionRepository).upsert(any())
        }

    @Test
    fun `should throw exception when validating withdraw for non-existent transaction`(): Unit =
        runBlocking {
            // Given
            val aggregateId = AggregateId("txn-not-found")
            val command =
                BankTransactionCommand.ValidateBankTransactionMoneyWithdraw(
                    aggregateId = aggregateId,
                    correlationId = "corr-004",
                )

            whenever(bankTransactionRepository.findById(aggregateId)).thenReturn(null)

            // When & Then
            assertThatThrownBy { runBlocking { commandHandler.handle(command) } }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("not found")
        }

    // ==================== ValidateBankTransactionMoneyDeposit Tests ====================

    @Test
    fun `should validate money deposit successfully`(): Unit =
        runBlocking {
            // Given
            val aggregateId = AggregateId("txn-002")
            val existingTransaction =
                createBankTransaction(
                    aggregateId = aggregateId,
                    moneyWithdrawn = true,
                    status = BankTransactionStatus.CREATED,
                )
            val command =
                BankTransactionCommand.ValidateBankTransactionMoneyDeposit(
                    aggregateId = aggregateId,
                    correlationId = "corr-005",
                )
            val expectedTransaction =
                createBankTransaction(
                    aggregateId = aggregateId,
                    moneyWithdrawn = true,
                    moneyDeposited = true,
                    status = BankTransactionStatus.CREATED,
                    version = 1L,
                )

            whenever(bankTransactionRepository.findById(aggregateId)).thenReturn(existingTransaction)
            whenever(bankTransactionRepository.upsert(any())).thenReturn(expectedTransaction)

            // When
            val result = commandHandler.handle(command)

            // Then
            assertThat(result.moneyWithdrawn).isTrue()
            assertThat(result.moneyDeposited).isTrue()
            verify(bankTransactionRepository).findById(aggregateId)
            verify(bankTransactionRepository).upsert(any())
        }

    // ==================== FinishBankTransaction Tests ====================

    @Test
    fun `should finish bankTransaction successfully`(): Unit =
        runBlocking {
            // Given
            val aggregateId = AggregateId("txn-003")
            val existingTransaction =
                createBankTransaction(
                    aggregateId = aggregateId,
                    moneyWithdrawn = true,
                    moneyDeposited = true,
                    status = BankTransactionStatus.CREATED,
                )
            val command =
                BankTransactionCommand.FinishBankTransaction(
                    aggregateId = aggregateId,
                    correlationId = "corr-006",
                    fromAccountId = AggregateId("acc-001"),
                    toAccountId = AggregateId("acc-002"),
                )
            val expectedTransaction =
                createBankTransaction(
                    aggregateId = aggregateId,
                    moneyWithdrawn = true,
                    moneyDeposited = true,
                    status = BankTransactionStatus.FINISHED,
                    version = 1L,
                )

            whenever(bankTransactionRepository.findById(aggregateId)).thenReturn(existingTransaction)
            whenever(bankTransactionRepository.upsert(any())).thenReturn(expectedTransaction)

            // When
            val result = commandHandler.handle(command)

            // Then
            assertThat(result.status).isEqualTo(BankTransactionStatus.FINISHED)
            verify(bankTransactionRepository).findById(aggregateId)
            verify(bankTransactionRepository).upsert(any())
        }

    // ==================== CancelBankTransaction Tests ====================

    @Test
    fun `should cancel bankTransaction successfully`(): Unit =
        runBlocking {
            // Given
            val aggregateId = AggregateId("txn-004")
            val existingTransaction =
                createBankTransaction(
                    aggregateId = aggregateId,
                    moneyWithdrawn = true,
                    status = BankTransactionStatus.CREATED,
                )
            val command =
                BankTransactionCommand.CancelBankTransaction(
                    aggregateId = aggregateId,
                    correlationId = "corr-007",
                    fromAccountId = AggregateId("acc-001"),
                    toAccountId = AggregateId("acc-002"),
                    amount = BigDecimal("100.00"),
                )
            val expectedTransaction =
                createBankTransaction(
                    aggregateId = aggregateId,
                    moneyWithdrawn = false,
                    status = BankTransactionStatus.FAILED,
                    version = 3L,
                )

            whenever(bankTransactionRepository.findById(aggregateId)).thenReturn(existingTransaction)
            whenever(bankTransactionRepository.upsert(any())).thenReturn(expectedTransaction)

            // When
            val result = commandHandler.handle(command)

            // Then
            assertThat(result.status).isEqualTo(BankTransactionStatus.FAILED)
            verify(bankTransactionRepository).findById(aggregateId)
            verify(bankTransactionRepository).upsert(any())
        }

    // ==================== Helper Methods ====================

    private fun createBankTransaction(
        aggregateId: AggregateId,
        correlationId: String = "corr-test",
        fromAccountId: AggregateId = AggregateId("acc-001"),
        toAccountId: AggregateId = AggregateId("acc-002"),
        amount: BigDecimal = BigDecimal("100.00"),
        moneyWithdrawn: Boolean = false,
        moneyDeposited: Boolean = false,
        status: BankTransactionStatus = BankTransactionStatus.INITIALIZED,
        version: Long = 0L,
    ): BankTransaction {
        val now = Instant.now()
        return BankTransaction(
            aggregateId = aggregateId,
            correlationId = correlationId,
            fromAccountId = fromAccountId,
            toAccountId = toAccountId,
            amount = amount,
            moneyWithdrawn = moneyWithdrawn,
            moneyDeposited = moneyDeposited,
            status = status,
            version = version,
            createdAt = now,
            updatedAt = now,
        )
    }

    /**
     * Fake LockProvider for testing that executes operations directly without actual locking.
     */
    private class FakeLockProvider : LockProvider {
        override suspend fun <T> withLock(
            keyLock: String,
            operation: suspend () -> T,
        ): T = operation()
    }
}
