package mz.data.platform.bankaccount.service.exceptions

class AccountNotFoundException(
    accountId: String,
) : RuntimeException("Account not found with id: $accountId")
