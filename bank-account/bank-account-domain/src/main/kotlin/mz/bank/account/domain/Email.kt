package mz.bank.account.domain

/**
 * Email value object with basic validation.
 * Ensures email format is valid according to basic rules.
 */
data class Email(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "Email cannot be blank" }
        require(value.contains("@")) { "Email must contain '@' symbol" }
        require(value.length <= MAX_LENGTH) { "Email cannot exceed $MAX_LENGTH characters" }

        val parts = value.split("@")
        require(parts.size == 2) { "Email must contain exactly one '@' symbol" }
        require(parts[0].isNotBlank()) { "Email local part cannot be blank" }
        require(parts[1].isNotBlank()) { "Email domain cannot be blank" }
        require(parts[1].contains(".")) { "Email domain must contain a dot" }
    }

    companion object {
        private const val MAX_LENGTH = 254
    }
}
