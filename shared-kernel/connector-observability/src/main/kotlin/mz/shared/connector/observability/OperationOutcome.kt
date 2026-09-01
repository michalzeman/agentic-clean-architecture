package mz.shared.connector.observability

enum class OperationOutcome {
    OK,
    FAIL,
    ;

    val tagValue: String
        get() = name.lowercase()
}
