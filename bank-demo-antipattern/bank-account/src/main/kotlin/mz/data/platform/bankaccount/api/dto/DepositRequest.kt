package mz.data.platform.bankaccount.api.dto

import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal

data class DepositRequest(
    @field:NotNull(message = "Amount is required")
    @field:DecimalMin(value = "0.01", inclusive = true, message = "Amount must be greater than zero")
    val amount: BigDecimal,
)
