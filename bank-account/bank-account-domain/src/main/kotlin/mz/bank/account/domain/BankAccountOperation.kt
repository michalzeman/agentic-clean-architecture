package mz.bank.account.domain

enum class BankAccountOperation {
    CREATE,
    DEPOSIT,
    WITHDRAW,
    TRANSFER_WITHDRAW,
    TRANSFER_DEPOSIT,
    ROLLBACK_WITHDRAW,
    ROLLBACK_DEPOSIT,
    FINISH,
}
