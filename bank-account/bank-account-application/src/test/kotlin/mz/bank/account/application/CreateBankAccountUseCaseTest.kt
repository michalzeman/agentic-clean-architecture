package mz.bank.account.application

import kotlinx.coroutines.runBlocking
import mz.bank.account.domain.BankAccountCommand
import mz.bank.account.domain.Email
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal

class CreateBankAccountUseCaseTest {
    private lateinit var bankAccountRepository: BankAccountRepository
    private lateinit var createBankAccountUseCase: CreateBankAccountUseCase

    @BeforeEach
    fun setUp() {
        bankAccountRepository = mock()
        createBankAccountUseCase = CreateBankAccountUseCase(bankAccountRepository)
    }

    @Test
    fun `should create account successfully when email does not exist`(): Unit =
        runBlocking {
            // Given
            val command =
                BankAccountCommand.CreateAccount(
                    email = Email("new@example.com"),
                    initialBalance = BigDecimal("100.00"),
                )

            whenever(bankAccountRepository.existsByEmail(command.email)).thenReturn(false)
            whenever(bankAccountRepository.upsert(any())).thenAnswer { invocation ->
                val aggregate = invocation.getArgument<mz.bank.account.domain.BankAccountAggregate>(0)
                aggregate.account
            }

            // When
            val result = createBankAccountUseCase.execute(command)

            // Then
            assertThat(result.email).isEqualTo(Email("new@example.com"))
            assertThat(result.amount).isEqualByComparingTo(BigDecimal("100.00"))
            assertThat(result.aggregateId.value).isNotBlank()
            verify(bankAccountRepository).existsByEmail(command.email)
            verify(bankAccountRepository).upsert(any())
        }

    @Test
    fun `should throw IllegalStateException when email already exists`(): Unit =
        runBlocking {
            // Given
            val command =
                BankAccountCommand.CreateAccount(
                    email = Email("existing@example.com"),
                    initialBalance = BigDecimal("100.00"),
                )

            whenever(bankAccountRepository.existsByEmail(command.email)).thenReturn(true)

            // When & Then
            assertThatThrownBy { runBlocking { createBankAccountUseCase.execute(command) } }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("Account with email existing@example.com already exists")
        }

    @Test
    fun `should create account with zero initial balance`(): Unit =
        runBlocking {
            // Given
            val command =
                BankAccountCommand.CreateAccount(
                    email = Email("zero@example.com"),
                    initialBalance = BigDecimal.ZERO,
                )

            whenever(bankAccountRepository.existsByEmail(command.email)).thenReturn(false)
            whenever(bankAccountRepository.upsert(any())).thenAnswer { invocation ->
                val aggregate = invocation.getArgument<mz.bank.account.domain.BankAccountAggregate>(0)
                aggregate.account
            }

            // When
            val result = createBankAccountUseCase.execute(command)

            // Then
            assertThat(result.amount).isEqualByComparingTo(BigDecimal.ZERO)
            verify(bankAccountRepository).existsByEmail(command.email)
            verify(bankAccountRepository).upsert(any())
        }

    @Test
    fun `should create account with large initial balance`(): Unit =
        runBlocking {
            // Given
            val command =
                BankAccountCommand.CreateAccount(
                    email = Email("wealthy@example.com"),
                    initialBalance = BigDecimal("1000000.00"),
                )

            whenever(bankAccountRepository.existsByEmail(command.email)).thenReturn(false)
            whenever(bankAccountRepository.upsert(any())).thenAnswer { invocation ->
                val aggregate = invocation.getArgument<mz.bank.account.domain.BankAccountAggregate>(0)
                aggregate.account
            }

            // When
            val result = createBankAccountUseCase.execute(command)

            // Then
            assertThat(result.amount).isEqualByComparingTo(BigDecimal("1000000.00"))
            verify(bankAccountRepository).existsByEmail(command.email)
            verify(bankAccountRepository).upsert(any())
        }
}
