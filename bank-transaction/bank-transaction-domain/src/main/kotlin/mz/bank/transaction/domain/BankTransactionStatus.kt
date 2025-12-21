package mz.bank.transaction.domain

/**
 * BankTransactionStatus represents the current state of a money transfer transaction.
 * Follows the saga pattern with clear state transitions.
 */
enum class BankTransactionStatus {
    /**
     * BankTransaction created but not started yet.
     */
    INITIALIZED,

    /**
     * BankTransaction in progress (saga executing).
     */
    CREATED,

    /**
     * BankTransaction completed successfully.
     */
    FINISHED,

    /**
     * BankTransaction failed and rolled back.
     */
    FAILED,
}
