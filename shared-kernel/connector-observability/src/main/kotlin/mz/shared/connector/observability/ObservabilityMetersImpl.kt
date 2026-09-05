package mz.shared.connector.observability

import io.micrometer.core.instrument.MeterRegistry
import mz.shared.domain.ObservabilityMeterNames.ACCOUNT_OPERATION
import mz.shared.domain.ObservabilityMeterNames.TRANSFER_COMPLETED
import mz.shared.domain.ObservabilityMeterNames.TRANSFER_ROLLED_BACK
import mz.shared.domain.ObservabilityMeters
import mz.shared.domain.OperationOutcome
import org.slf4j.LoggerFactory

/**
 * Bounded business counters for Phase 1. Domain-agnostic by design (spec D8):
 * tag values arrive as plain strings; callers map domain enums via name.lowercase().
 * Increments never throw — a broken registry logs WARN and the business operation continues.
 */
class ObservabilityMetersImpl(
    private val meterRegistry: MeterRegistry,
) : ObservabilityMeters {
    override fun transferCompleted(outcome: OperationOutcome) {
        increment(TRANSFER_COMPLETED, "outcome" to outcome.tagValue)
    }

    override fun transferRolledBack(reason: String) {
        increment(TRANSFER_ROLLED_BACK, "reason" to reason)
    }

    override fun accountOperation(
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
        val logger = LoggerFactory.getLogger(ObservabilityMetersImpl::class.java)
    }
}
