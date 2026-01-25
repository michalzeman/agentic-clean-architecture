package mz.data.platform.bankaccount.service.exceptions

import java.math.BigDecimal

class NegativeBalanceException(
    amount: BigDecimal,
) : RuntimeException("Balance cannot be negative. Attempted balance: $amount")
