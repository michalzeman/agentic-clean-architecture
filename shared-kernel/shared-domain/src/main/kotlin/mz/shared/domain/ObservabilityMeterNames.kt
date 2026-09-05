package mz.shared.domain

/**
 * Meter name constants shared between the ObservabilityMeters port and its consumers/tests.
 */
object ObservabilityMeterNames {
    const val TRANSFER_COMPLETED = "bank.transfer.completed"
    const val TRANSFER_ROLLED_BACK = "bank.transfer.rolled_back"
    const val ACCOUNT_OPERATION = "bank.account.operation"
}
