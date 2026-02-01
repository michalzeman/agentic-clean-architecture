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
        require(value.length <= MAX_LENGTH) { "Email cannot exceed $MAX_LENGTH characters" }
        require(EMAIL_REGEX.matches(value)) { "Email format is invalid: '$value'" }
    }

    companion object {
        private const val MAX_LENGTH = 254

        /**
         * Basic email format validation. Rejects obviously invalid formats
         * like "test@.com" or "test@test." but does not enforce full RFC 5321 compliance.
         */
        private val EMAIL_REGEX =
            Regex(
                "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$",
            )
    }
}
