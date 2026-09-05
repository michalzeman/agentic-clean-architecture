package mz.bank.system.tests

import mz.bank.transaction.contract.proto.BankTransactionEvent
import org.springframework.messaging.Message
import tools.jackson.databind.JsonNode

/**
 * Returns the protobuf `oneof` case name carried by a [BankTransactionEvent],
 * e.g. `transactionCreated` or `transactionFinished`. Used for failure diagnostics.
 */
internal fun BankTransactionEvent.eventCaseName(): String =
    when {
        hasTransactionCreated() -> "transactionCreated"
        hasTransactionMoneyWithdrawn() -> "transactionMoneyWithdrawn"
        hasTransactionMoneyDeposited() -> "transactionMoneyDeposited"
        hasTransactionFinished() -> "transactionFinished"
        hasTransactionFailed() -> "transactionFailed"
        hasTransactionRolledBack() -> "transactionRolledBack"
        hasTransactionWithdrawRolledBack() -> "transactionWithdrawRolledBack"
        hasTransactionDepositRolledBack() -> "transactionDepositRolledBack"
        else -> "unknown"
    }

/**
 * Summarizes the messages received on the transaction events channel: total count,
 * how many parsed as [BankTransactionEvent], and which oneof case each carries.
 * Built so a stalled saga is diagnosable from the test XML alone.
 */
internal fun describeTransactionEventMessages(messages: List<Message<*>>): String {
    val events = messages.mapNotNull { it.payload as? BankTransactionEvent }
    val eventCases = events.map { it.eventCaseName() }
    val unparsedCount = messages.size - events.size
    return "received ${messages.size} message(s) on bankTransactionEventsChannel, " +
        "of which ${events.size} parsed as BankTransactionEvent; " +
        "event discriminators: [${eventCases.joinToString(", ")}]" +
        (if (unparsedCount > 0) "; unparsed payloads: $unparsedCount" else "")
}

/**
 * Describes the transaction's current status and flags from its HTTP API response.
 * A null state (fetch failed) is reported as unavailable.
 */
internal fun describeTransactionState(state: JsonNode?): String {
    if (state == null) return "transaction state unavailable"
    val status = state.get("status")?.stringValue() ?: "<missing>"
    val moneyWithdrawn = state.get("moneyWithdrawn")?.asBoolean()
    val moneyDeposited = state.get("moneyDeposited")?.asBoolean()
    return "transaction state: status=$status, moneyWithdrawn=$moneyWithdrawn, moneyDeposited=$moneyDeposited"
}

/**
 * Running state of one JetStream consumer bean, captured for diagnostics.
 */
internal data class ConsumerRunningState(
    val consumerName: String,
    val isRunning: Boolean,
)

/**
 * Describes the isRunning state of each test JetStream consumer.
 * A stopped consumer (or missing bean) explains missing events in the test.
 */
internal fun describeConsumerRunningStates(states: List<ConsumerRunningState>): String =
    if (states.isEmpty()) {
        "no JetStream consumer beans found"
    } else {
        states.joinToString("; ") { "${it.consumerName}: isRunning=${it.isRunning}" }
    }
