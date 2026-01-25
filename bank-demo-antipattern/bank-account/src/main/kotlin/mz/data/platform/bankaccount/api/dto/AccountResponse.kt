package mz.data.platform.bankaccount.api.dto

import mz.data.platform.bankaccount.domain.BankAccount
import java.math.BigDecimal
import java.time.LocalDateTime

data class AccountResponse(
    val id: String,
    val accountNumber: String,
    val balance: BigDecimal,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
) {
    companion object {
        fun from(account: BankAccount): AccountResponse =
            AccountResponse(
                id = account.id,
                accountNumber = account.accountNumber,
                balance = account.balance,
                createdAt = account.createdAt,
                updatedAt = account.updatedAt,
            )
    }
}
