package mz.data.platform.bankaccount.domain

import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface AccountRepository : CrudRepository<BankAccount, String> {
    fun findByAccountNumber(accountNumber: String): BankAccount?
}
