package mz.bank.transaction.domain

/**
 * Bounded enum of reasons a bank transaction was rolled back.
 * The `bank.transfer.rolled_back` custom meter tags with the lowercase name
 * of whichever value the aggregate chose at cancellation time.
 * Tag values are closed — adding a new reason is a code change, preventing tag explosion.
 */
enum class TransactionRollbackReason {
    TIMEOUT,
    DEPOSIT_FAILED,
    WITHDRAWAL_FAILED,
    MANUAL_CANCEL,
    UNKNOWN,
}
