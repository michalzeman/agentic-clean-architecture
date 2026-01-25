package mz.data.platform.bankaccount.service

import mz.data.platform.bankaccount.domain.AccountRepository
import mz.data.platform.bankaccount.domain.BankAccount
import mz.data.platform.bankaccount.service.exceptions.AccountNotFoundException
import mz.data.platform.bankaccount.service.exceptions.InsufficientFundsException
import mz.data.platform.bankaccount.service.exceptions.NegativeBalanceException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

@Service
class BankAccountService(
    private val accountRepository: AccountRepository,
) {
    @Transactional
    fun createAccount(initialBalance: BigDecimal): BankAccount {
        if (initialBalance < BigDecimal.ZERO) {
            throw NegativeBalanceException(initialBalance)
        }

        val now = LocalDateTime.now()
        val account =
            BankAccount(
                id = UUID.randomUUID().toString(),
                accountNumber = generateAccountNumber(),
                balance = initialBalance,
                createdAt = now,
                updatedAt = now,
            )

        return accountRepository.save(account)
    }

    @Transactional
    fun withdraw(
        accountId: String,
        amount: BigDecimal,
    ): BankAccount {
        validatePositiveAmount(amount)

        val account = getAccountOrThrow(accountId)
        val newBalance = account.balance - amount

        if (newBalance < BigDecimal.ZERO) {
            throw InsufficientFundsException(accountId, account.balance, amount)
        }

        account.balance = newBalance
        account.updatedAt = LocalDateTime.now()

        return accountRepository.save(account)
    }

    @Transactional
    fun deposit(
        accountId: String,
        amount: BigDecimal,
    ): BankAccount {
        validatePositiveAmount(amount)

        val account = getAccountOrThrow(accountId)
        account.balance = account.balance + amount
        account.updatedAt = LocalDateTime.now()

        return accountRepository.save(account)
    }

    @Transactional
    fun transfer(
        fromAccountId: String,
        toAccountId: String,
        amount: BigDecimal,
    ): BankAccount {
        validatePositiveAmount(amount)

        if (fromAccountId == toAccountId) {
            throw IllegalArgumentException("Cannot transfer to the same account")
        }

        // Verify target account exists
        getAccountOrThrow(toAccountId)

        // Withdraw from source account
        val fromAccount = getAccountOrThrow(fromAccountId)
        val newBalance = fromAccount.balance - amount

        if (newBalance < BigDecimal.ZERO) {
            throw InsufficientFundsException(fromAccountId, fromAccount.balance, amount)
        }

        fromAccount.balance = newBalance
        fromAccount.updatedAt = LocalDateTime.now()
        val updatedFromAccount = accountRepository.save(fromAccount)

        // Deposit to target account
        try {
            deposit(toAccountId, amount)
        } catch (e: Exception) {
            // Rollback: restore funds to source account
            fromAccount.balance = fromAccount.balance + amount
            accountRepository.save(fromAccount)
            throw e
        }

        return updatedFromAccount
    }

    fun getAccount(accountId: String): BankAccount = getAccountOrThrow(accountId)

    private fun getAccountOrThrow(accountId: String): BankAccount =
        accountRepository
            .findById(accountId)
            .orElseThrow { AccountNotFoundException(accountId) }

    private fun validatePositiveAmount(amount: BigDecimal) {
        if (amount <= BigDecimal.ZERO) {
            throw IllegalArgumentException("Amount must be positive: $amount")
        }
    }

    private fun generateAccountNumber(): String {
        // Generate a 10-digit account number
        return (1000000000L..9999999999L).random().toString()
    }
}
