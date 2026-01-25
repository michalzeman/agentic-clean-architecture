package mz.data.platform.bankaccount.service

import mz.data.platform.bankaccount.domain.AccountRepository
import mz.data.platform.bankaccount.domain.BankAccount
import mz.data.platform.bankaccount.service.exceptions.AccountNotFoundException
import mz.data.platform.bankaccount.service.exceptions.InsufficientFundsException
import mz.data.platform.bankaccount.service.exceptions.NegativeBalanceException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class BankAccountServiceTest {
    @Mock
    private lateinit var accountRepository: AccountRepository

    @InjectMocks
    private lateinit var bankAccountService: BankAccountService

    @Test
    fun `createAccount should create account with valid initial balance`() {
        // Given
        val initialBalance = BigDecimal("1000.00")
        val captor = argumentCaptor<BankAccount>()

        whenever(accountRepository.save(any())).thenAnswer { it.arguments[0] }

        // When
        val result = bankAccountService.createAccount(initialBalance)

        // Then
        verify(accountRepository).save(captor.capture())
        val savedAccount = captor.firstValue

        assertThat(savedAccount.balance).isEqualTo(initialBalance)
        assertThat(savedAccount.id).isNotBlank()
        assertThat(savedAccount.accountNumber).isNotBlank()
        assertThat(result.balance).isEqualTo(initialBalance)
    }

    @Test
    fun `createAccount should throw exception for negative balance`() {
        // Given
        val negativeBalance = BigDecimal("-100.00")

        // When & Then
        assertThrows<NegativeBalanceException> {
            bankAccountService.createAccount(negativeBalance)
        }
    }

    @Test
    fun `withdraw should decrease balance when sufficient funds`() {
        // Given
        val accountId = "test-id"
        val initialBalance = BigDecimal("1000.00")
        val withdrawAmount = BigDecimal("300.00")
        val account = createTestAccount(accountId, initialBalance)

        whenever(accountRepository.findById(accountId)).thenReturn(Optional.of(account))
        whenever(accountRepository.save(any())).thenAnswer { it.arguments[0] }

        // When
        val result = bankAccountService.withdraw(accountId, withdrawAmount)

        // Then
        assertThat(result.balance).isEqualTo(BigDecimal("700.00"))
        verify(accountRepository).save(any())
    }

    @Test
    fun `withdraw should throw exception when insufficient funds`() {
        // Given
        val accountId = "test-id"
        val initialBalance = BigDecimal("100.00")
        val withdrawAmount = BigDecimal("200.00")
        val account = createTestAccount(accountId, initialBalance)

        whenever(accountRepository.findById(accountId)).thenReturn(Optional.of(account))

        // When & Then
        assertThrows<InsufficientFundsException> {
            bankAccountService.withdraw(accountId, withdrawAmount)
        }
    }

    @Test
    fun `withdraw should throw exception for negative amount`() {
        // Given
        val accountId = "test-id"

        // When & Then
        assertThrows<IllegalArgumentException> {
            bankAccountService.withdraw(accountId, BigDecimal("-50.00"))
        }
    }

    @Test
    fun `deposit should increase balance`() {
        // Given
        val accountId = "test-id"
        val initialBalance = BigDecimal("1000.00")
        val depositAmount = BigDecimal("500.00")
        val account = createTestAccount(accountId, initialBalance)

        whenever(accountRepository.findById(accountId)).thenReturn(Optional.of(account))
        whenever(accountRepository.save(any())).thenAnswer { it.arguments[0] }

        // When
        val result = bankAccountService.deposit(accountId, depositAmount)

        // Then
        assertThat(result.balance).isEqualTo(BigDecimal("1500.00"))
        verify(accountRepository).save(any())
    }

    @Test
    fun `deposit should throw exception for negative amount`() {
        // Given
        val accountId = "test-id"

        // When & Then
        assertThrows<IllegalArgumentException> {
            bankAccountService.deposit(accountId, BigDecimal("-50.00"))
        }
    }

    @Test
    fun `transfer should move money between accounts`() {
        // Given
        val fromAccountId = "from-id"
        val toAccountId = "to-id"
        val transferAmount = BigDecimal("300.00")
        val fromAccount = createTestAccount(fromAccountId, BigDecimal("1000.00"))
        val toAccount = createTestAccount(toAccountId, BigDecimal("500.00"))

        whenever(accountRepository.findById(fromAccountId)).thenReturn(Optional.of(fromAccount))
        whenever(accountRepository.findById(toAccountId)).thenReturn(Optional.of(toAccount))
        whenever(accountRepository.save(any())).thenAnswer { it.arguments[0] }

        // When
        val result = bankAccountService.transfer(fromAccountId, toAccountId, transferAmount)

        // Then
        assertThat(result.balance).isEqualTo(BigDecimal("700.00"))
    }

    @Test
    fun `transfer should throw exception when insufficient funds`() {
        // Given
        val fromAccountId = "from-id"
        val toAccountId = "to-id"
        val transferAmount = BigDecimal("1500.00")
        val fromAccount = createTestAccount(fromAccountId, BigDecimal("1000.00"))
        val toAccount = createTestAccount(toAccountId, BigDecimal("500.00"))

        whenever(accountRepository.findById(fromAccountId)).thenReturn(Optional.of(fromAccount))
        whenever(accountRepository.findById(toAccountId)).thenReturn(Optional.of(toAccount))

        // When & Then
        assertThrows<InsufficientFundsException> {
            bankAccountService.transfer(fromAccountId, toAccountId, transferAmount)
        }
    }

    @Test
    fun `transfer should throw exception when target account does not exist`() {
        // Given
        val fromAccountId = "from-id"
        val toAccountId = "non-existent-id"
        val transferAmount = BigDecimal("300.00")

        whenever(accountRepository.findById(toAccountId)).thenReturn(Optional.empty())

        // When & Then
        assertThrows<AccountNotFoundException> {
            bankAccountService.transfer(fromAccountId, toAccountId, transferAmount)
        }
    }

    @Test
    fun `transfer should throw exception when transferring to same account`() {
        // Given
        val accountId = "test-id"
        val transferAmount = BigDecimal("300.00")

        // When & Then
        assertThrows<IllegalArgumentException> {
            bankAccountService.transfer(accountId, accountId, transferAmount)
        }
    }

    @Test
    fun `getAccount should return account when exists`() {
        // Given
        val accountId = "test-id"
        val account = createTestAccount(accountId, BigDecimal("1000.00"))

        whenever(accountRepository.findById(accountId)).thenReturn(Optional.of(account))

        // When
        val result = bankAccountService.getAccount(accountId)

        // Then
        assertThat(result.id).isEqualTo(accountId)
        assertThat(result.balance).isEqualTo(BigDecimal("1000.00"))
    }

    @Test
    fun `getAccount should throw exception when account does not exist`() {
        // Given
        val accountId = "non-existent-id"

        whenever(accountRepository.findById(accountId)).thenReturn(Optional.empty())

        // When & Then
        assertThrows<AccountNotFoundException> {
            bankAccountService.getAccount(accountId)
        }
    }

    private fun createTestAccount(
        id: String,
        balance: BigDecimal,
    ): BankAccount {
        val now = LocalDateTime.now()
        return BankAccount(
            id = id,
            accountNumber = "ACC$id",
            balance = balance,
            createdAt = now,
            updatedAt = now,
        )
    }
}
