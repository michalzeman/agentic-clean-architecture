package mz.bank.transaction.application.transaction

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import mz.bank.transaction.application.account.AccountViewRepository
import mz.bank.transaction.domain.BankTransaction
import mz.bank.transaction.domain.BankTransactionAggregate
import mz.bank.transaction.domain.BankTransactionCommand
import mz.shared.domain.LockProvider
import org.springframework.stereotype.Component

@Component
class CreateTransactionUseCase(
    private val accountViewRepository: AccountViewRepository,
    private val bankTransactionRepository: BankTransactionRepository,
    private val lockProvider: LockProvider,
) {
    suspend operator fun invoke(command: BankTransactionCommand.CreateBankTransaction): BankTransaction {
        val (firstLockId, secondLockId) =
            if (command.fromAccountId.value.hashCode() < command.toAccountId.value.hashCode()) {
                command.fromAccountId.value to command.toAccountId.value
            } else {
                command.toAccountId.value to command.fromAccountId.value
            }

        return lockProvider.withLock(firstLockId) {
            lockProvider.withLock(secondLockId) {
                coroutineScope {
                    val fromAccount = async { accountViewRepository.findById(command.fromAccountId) }
                    val toAccount = async { accountViewRepository.findById(command.toAccountId) }

                    requireNotNull(fromAccount.await()) { "From account not found: ${command.fromAccountId.value}" }
                    requireNotNull(toAccount.await()) { "To account not found: ${command.toAccountId.value}" }
                }

                val aggregate = BankTransactionAggregate.create(command)
                bankTransactionRepository.upsert(aggregate)
            }
        }
    }
}
