package mz.data.platform.bankaccount.api.dto

import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal

data class TransferRequest(
    @field:NotBlank(message = "Target account ID is required")
    val toAccountId: String,
    @field:NotNull(message = "Amount is required")
    @field:DecimalMin(value = "0.01", inclusive = true, message = "Amount must be greater than zero")
    val amount: BigDecimal,
)
