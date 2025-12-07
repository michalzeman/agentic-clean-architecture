package mz.bank.account.application

import mz.bank.account.domain.BankAccount
import mz.bank.account.domain.BankAccountAggregate
import mz.bank.account.domain.BankAccountCommand
import org.springframework.stereotype.Component

/**
 * Use case for creating a new bank account.
 * Encapsulates the entire creation workflow including:
 * - Email uniqueness validation
 * - Aggregate creation via domain logic
 * - Persistence via repository
 */
@Component
class CreateBankAccountUseCase(
    private val bankAccountRepository: BankAccountRepository,
) {
    /**
     * Executes the create account use case.
     * @param command The create account command
     * @return The created BankAccount
     * @throws IllegalStateException if an account with the given email already exists
     */
    suspend fun execute(command: BankAccountCommand.CreateAccount): BankAccount {
        // Check email uniqueness
        if (bankAccountRepository.existsByEmail(command.email)) {
            throw IllegalStateException("Account with email ${command.email.value} already exists")
        }

        // Create aggregate using domain logic
        val aggregate = BankAccountAggregate.create(command)

        // Persist and return
        return bankAccountRepository.upsert(aggregate)
    }
}
