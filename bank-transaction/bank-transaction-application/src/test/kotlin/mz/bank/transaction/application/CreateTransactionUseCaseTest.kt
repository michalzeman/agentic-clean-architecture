package mz.bank.transaction.application

import kotlinx.coroutines.runBlocking
import mz.bank.transaction.application.account.AccountViewRepository
import mz.bank.transaction.application.transaction.BankTransactionRepository
import mz.bank.transaction.application.transaction.CreateTransactionUseCase
import mz.bank.transaction.domain.BankTransactionCommand
import mz.bank.transaction.domain.BankTransactionStatus
import mz.bank.transaction.domain.account.AccountView
import mz.shared.domain.AggregateId
import mz.shared.domain.LockProvider
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal

class CreateTransactionUseCaseTest {
    private lateinit var accountViewRepository: AccountViewRepository
    private lateinit var bankTransactionRepository: BankTransactionRepository
    private lateinit var lockProvider: LockProvider
    private lateinit var createTransactionUseCase: CreateTransactionUseCase

    private val fromAccountId = AggregateId("acc-001")
    private val toAccountId = AggregateId("acc-002")

    @BeforeEach
    fun setUp() {
        accountViewRepository = mock()
        bankTransactionRepository = mock()
        lockProvider = FakeLockProvider()
        createTransactionUseCase =
            CreateTransactionUseCase(
                accountViewRepository,
                bankTransactionRepository,
                lockProvider,
            )
    }

    @Test
    fun `should create transaction when both accounts exist`(): Unit =
        runBlocking {
            // Given
            val command = createCommand()
            whenever(accountViewRepository.findById(fromAccountId)).thenReturn(AccountView(fromAccountId))
            whenever(accountViewRepository.findById(toAccountId)).thenReturn(AccountView(toAccountId))
            whenever(bankTransactionRepository.upsert(any())).thenAnswer { invocation ->
                invocation.getArgument<mz.bank.transaction.domain.BankTransactionAggregate>(0).bankTransaction
            }

            // When
            val result = createTransactionUseCase(command)

            // Then
            assertThat(result.aggregateId.value).isNotBlank()
            assertThat(result.correlationId).isEqualTo(command.correlationId)
            assertThat(result.fromAccountId).isEqualTo(fromAccountId)
            assertThat(result.toAccountId).isEqualTo(toAccountId)
            assertThat(result.amount).isEqualByComparingTo(BigDecimal("100.00"))
            assertThat(result.status).isEqualTo(BankTransactionStatus.CREATED)
            verify(accountViewRepository).findById(fromAccountId)
            verify(accountViewRepository).findById(toAccountId)
            verify(bankTransactionRepository).upsert(any())
        }

    @Test
    fun `should throw exception when from account does not exist`(): Unit =
        runBlocking {
            // Given
            val command = createCommand()
            whenever(accountViewRepository.findById(fromAccountId)).thenReturn(null)
            whenever(accountViewRepository.findById(toAccountId)).thenReturn(AccountView(toAccountId))

            // When & Then
            assertThatThrownBy { runBlocking { createTransactionUseCase(command) } }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("From account not found")
                .hasMessageContaining(fromAccountId.value)

            verify(bankTransactionRepository, never()).upsert(any())
        }

    @Test
    fun `should throw exception when to account does not exist`(): Unit =
        runBlocking {
            // Given
            val command = createCommand()
            whenever(accountViewRepository.findById(fromAccountId)).thenReturn(AccountView(fromAccountId))
            whenever(accountViewRepository.findById(toAccountId)).thenReturn(null)

            // When & Then
            assertThatThrownBy { runBlocking { createTransactionUseCase(command) } }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("To account not found")
                .hasMessageContaining(toAccountId.value)

            verify(bankTransactionRepository, never()).upsert(any())
        }

    @Test
    fun `should throw exception when both accounts do not exist`(): Unit =
        runBlocking {
            // Given
            val command = createCommand()
            whenever(accountViewRepository.findById(fromAccountId)).thenReturn(null)
            whenever(accountViewRepository.findById(toAccountId)).thenReturn(null)

            // When & Then
            assertThatThrownBy { runBlocking { createTransactionUseCase(command) } }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("account not found")

            verify(bankTransactionRepository, never()).upsert(any())
        }

    private fun createCommand(
        correlationId: String = "corr-001",
        fromAccountId: AggregateId = this.fromAccountId,
        toAccountId: AggregateId = this.toAccountId,
        amount: BigDecimal = BigDecimal("100.00"),
    ) = BankTransactionCommand.CreateBankTransaction(
        correlationId = correlationId,
        fromAccountId = fromAccountId,
        toAccountId = toAccountId,
        amount = amount,
    )

    private class FakeLockProvider : LockProvider {
        override suspend fun <T> withLock(
            keyLock: String,
            operation: suspend () -> T,
        ): T = operation()
    }
}
