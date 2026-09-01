package mz.shared.connector.observability

import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory

/**
 * Bounded business counters for Phase 1. Domain-agnostic by design (spec D8):
 * tag values arrive as plain strings; callers map domain enums via name.lowercase().
 * Increments never throw — a broken registry logs WARN and the business operation continues.
 */
class ObservabilityMeters(
    private val meterRegistry: MeterRegistry,
) {
    fun transferCompleted(outcome: OperationOutcome) {
        increment(TRANSFER_COMPLETED, "outcome" to outcome.tagValue)
    }

    fun transferRolledBack(reason: String) {
        increment(TRANSFER_ROLLED_BACK, "reason" to reason)
    }

    fun accountOperation(
        op: String,
        outcome: OperationOutcome,
    ) {
        increment(ACCOUNT_OPERATION, "op" to op, "outcome" to outcome.tagValue)
    }

    private fun increment(
        name: String,
        vararg tags: Pair<String, String>,
    ) {
        try {
            val tagStrings = tags.flatMap { (key, value) -> listOf(key, value) }.toTypedArray()
            meterRegistry.counter(name, *tagStrings).increment()
        } catch (e: Exception) {
            logger.warn("Meter increment failed for {} — metric dropped, business operation continues", name, e)
        }
    }

    private companion object {
        const val TRANSFER_COMPLETED = "bank.transfer.completed"
        const val TRANSFER_ROLLED_BACK = "bank.transfer.rolled_back"
        const val ACCOUNT_OPERATION = "bank.account.operation"
        val logger = LoggerFactory.getLogger(ObservabilityMeters::class.java)
    }
}
