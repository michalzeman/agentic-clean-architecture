package mz.shared.domain

/**
 * Port for business observability counters.
 * Application layer depends on this interface; connector-observability provides the implementation.
 */
interface ObservabilityMeters {
    fun transferCompleted(outcome: OperationOutcome)

    fun transferRolledBack(reason: String)

    fun accountOperation(
        op: String,
        outcome: OperationOutcome,
    )
}
