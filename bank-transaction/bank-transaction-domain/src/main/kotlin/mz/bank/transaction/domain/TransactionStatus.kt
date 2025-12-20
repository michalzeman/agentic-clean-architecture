package mz.bank.transaction.domain

/**
 * TransactionStatus represents the current state of a money transfer transaction.
 * Follows the saga pattern with clear state transitions.
 */
enum class TransactionStatus {
    /**
     * Transaction created but not started yet.
     */
    INITIALIZED,

    /**
     * Transaction in progress (saga executing).
     */
    CREATED,

    /**
     * Transaction completed successfully.
     */
    FINISHED,

    /**
     * Transaction failed and rolled back.
     */
    FAILED,
}
