package mz.shared.domain

enum class OperationOutcome {
    OK,
    FAIL,
    ;

    val tagValue: String
        get() = name.lowercase()
}
