package mz.data.platform.bankaccount.api.dto

import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal

data class CreateAccountRequest(
    @field:NotNull(message = "Initial balance is required")
    @field:DecimalMin(value = "0.0", inclusive = true, message = "Initial balance cannot be negative")
    val initialBalance: BigDecimal,
)
