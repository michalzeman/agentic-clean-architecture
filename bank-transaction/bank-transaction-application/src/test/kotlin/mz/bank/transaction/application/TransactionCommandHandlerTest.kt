package mz.bank.transaction.application

import kotlinx.coroutines.runBlocking
import mz.bank.transaction.domain.Transaction
import mz.bank.transaction.domain.TransactionCommand
import mz.bank.transaction.domain.TransactionStatus
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

class TransactionCommandHandlerTest {
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var lockProvider: LockProvider
    private lateinit var commandHandler: TransactionCommandHandler

    @BeforeEach
    fun setUp() {
        transactionRepository = mock()
        lockProvider = FakeLockProvider()
        commandHandler = TransactionCommandHandler(transactionRepository, lockProvider)
    }

    // ==================== CreateTransaction Tests ====================

    @Test
    fun `should create transaction successfully`(): Unit =
        runBlocking {
            // Given
            val command =
                TransactionCommand.CreateTransaction(
                    correlationId = "corr-001",
                    fromAccountId = AggregateId("acc-001"),
                    toAccountId = AggregateId("acc-002"),
                    amount = BigDecimal("100.00"),
                )

            whenever(transactionRepository.upsert(any())).thenAnswer { invocation ->
                val aggregate = invocation.getArgument<mz.bank.transaction.domain.TransactionAggregate>(0)
                aggregate.transaction
            }

            // When
            val result = commandHandler.handle(command)

            // Then
            assertThat(result.aggregateId.value).isNotBlank()
            assertThat(result.correlationId).isEqualTo("corr-001")
            assertThat(result.fromAccountId).isEqualTo(AggregateId("acc-001"))
            assertThat(result.toAccountId).isEqualTo(AggregateId("acc-002"))
            assertThat(result.amount).isEqualByComparingTo(BigDecimal("100.00"))
            assertThat(result.status).isEqualTo(TransactionStatus.CREATED)
            verify(transactionRepository).upsert(any())
        }

    @Test
    fun `should throw exception when creating transaction with zero amount`(): Unit =
        runBlocking {
            // Given
            val command =
                TransactionCommand.CreateTransaction(
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

    // ==================== ValidateTransactionMoneyWithdraw Tests ====================

    @Test
    fun `should validate money withdraw successfully`(): Unit =
        runBlocking {
            // Given
            val aggregateId = AggregateId("txn-001")
            val existingTransaction =
                createTransaction(
                    aggregateId = aggregateId,
                    status = TransactionStatus.CREATED,
                )
            val command =
                TransactionCommand.ValidateTransactionMoneyWithdraw(
                    aggregateId = aggregateId,
                    correlationId = "corr-003",
                )
            val expectedTransaction =
                createTransaction(
                    aggregateId = aggregateId,
                    moneyWithdrawn = true,
                    status = TransactionStatus.CREATED,
                    version = 1L,
                )

            whenever(transactionRepository.findById(aggregateId)).thenReturn(existingTransaction)
            whenever(transactionRepository.upsert(any())).thenReturn(expectedTransaction)

            // When
            val result = commandHandler.handle(command)

            // Then
            assertThat(result.moneyWithdrawn).isTrue()
            verify(transactionRepository).findById(aggregateId)
            verify(transactionRepository).upsert(any())
        }

    @Test
    fun `should throw exception when validating withdraw for non-existent transaction`(): Unit =
        runBlocking {
            // Given
            val aggregateId = AggregateId("txn-not-found")
            val command =
                TransactionCommand.ValidateTransactionMoneyWithdraw(
                    aggregateId = aggregateId,
                    correlationId = "corr-004",
                )

            whenever(transactionRepository.findById(aggregateId)).thenReturn(null)

            // When & Then
            assertThatThrownBy { runBlocking { commandHandler.handle(command) } }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("not found")
        }

    // ==================== ValidateTransactionMoneyDeposit Tests ====================

    @Test
    fun `should validate money deposit successfully`(): Unit =
        runBlocking {
            // Given
            val aggregateId = AggregateId("txn-002")
            val existingTransaction =
                createTransaction(
                    aggregateId = aggregateId,
                    moneyWithdrawn = true,
                    status = TransactionStatus.CREATED,
                )
            val command =
                TransactionCommand.ValidateTransactionMoneyDeposit(
                    aggregateId = aggregateId,
                    correlationId = "corr-005",
                )
            val expectedTransaction =
                createTransaction(
                    aggregateId = aggregateId,
                    moneyWithdrawn = true,
                    moneyDeposited = true,
                    status = TransactionStatus.CREATED,
                    version = 1L,
                )

            whenever(transactionRepository.findById(aggregateId)).thenReturn(existingTransaction)
            whenever(transactionRepository.upsert(any())).thenReturn(expectedTransaction)

            // When
            val result = commandHandler.handle(command)

            // Then
            assertThat(result.moneyWithdrawn).isTrue()
            assertThat(result.moneyDeposited).isTrue()
            verify(transactionRepository).findById(aggregateId)
            verify(transactionRepository).upsert(any())
        }

    // ==================== FinishTransaction Tests ====================

    @Test
    fun `should finish transaction successfully`(): Unit =
        runBlocking {
            // Given
            val aggregateId = AggregateId("txn-003")
            val existingTransaction =
                createTransaction(
                    aggregateId = aggregateId,
                    moneyWithdrawn = true,
                    moneyDeposited = true,
                    status = TransactionStatus.CREATED,
                )
            val command =
                TransactionCommand.FinishTransaction(
                    aggregateId = aggregateId,
                    correlationId = "corr-006",
                    fromAccountId = AggregateId("acc-001"),
                    toAccountId = AggregateId("acc-002"),
                )
            val expectedTransaction =
                createTransaction(
                    aggregateId = aggregateId,
                    moneyWithdrawn = true,
                    moneyDeposited = true,
                    status = TransactionStatus.FINISHED,
                    version = 1L,
                )

            whenever(transactionRepository.findById(aggregateId)).thenReturn(existingTransaction)
            whenever(transactionRepository.upsert(any())).thenReturn(expectedTransaction)

            // When
            val result = commandHandler.handle(command)

            // Then
            assertThat(result.status).isEqualTo(TransactionStatus.FINISHED)
            verify(transactionRepository).findById(aggregateId)
            verify(transactionRepository).upsert(any())
        }

    // ==================== CancelTransaction Tests ====================

    @Test
    fun `should cancel transaction successfully`(): Unit =
        runBlocking {
            // Given
            val aggregateId = AggregateId("txn-004")
            val existingTransaction =
                createTransaction(
                    aggregateId = aggregateId,
                    moneyWithdrawn = true,
                    status = TransactionStatus.CREATED,
                )
            val command =
                TransactionCommand.CancelTransaction(
                    aggregateId = aggregateId,
                    correlationId = "corr-007",
                    fromAccountId = AggregateId("acc-001"),
                    toAccountId = AggregateId("acc-002"),
                    amount = BigDecimal("100.00"),
                )
            val expectedTransaction =
                createTransaction(
                    aggregateId = aggregateId,
                    moneyWithdrawn = false,
                    status = TransactionStatus.FAILED,
                    version = 3L,
                )

            whenever(transactionRepository.findById(aggregateId)).thenReturn(existingTransaction)
            whenever(transactionRepository.upsert(any())).thenReturn(expectedTransaction)

            // When
            val result = commandHandler.handle(command)

            // Then
            assertThat(result.status).isEqualTo(TransactionStatus.FAILED)
            verify(transactionRepository).findById(aggregateId)
            verify(transactionRepository).upsert(any())
        }

    // ==================== Helper Methods ====================

    private fun createTransaction(
        aggregateId: AggregateId,
        correlationId: String = "corr-test",
        fromAccountId: AggregateId = AggregateId("acc-001"),
        toAccountId: AggregateId = AggregateId("acc-002"),
        amount: BigDecimal = BigDecimal("100.00"),
        moneyWithdrawn: Boolean = false,
        moneyDeposited: Boolean = false,
        status: TransactionStatus = TransactionStatus.INITIALIZED,
        version: Long = 0L,
    ): Transaction {
        val now = Instant.now()
        return Transaction(
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
