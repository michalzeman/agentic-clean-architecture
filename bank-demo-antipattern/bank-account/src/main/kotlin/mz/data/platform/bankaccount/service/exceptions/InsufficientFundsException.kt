package mz.data.platform.bankaccount.service.exceptions

import java.math.BigDecimal

class InsufficientFundsException(
    accountId: String,
    currentBalance: BigDecimal,
    requestedAmount: BigDecimal,
) : RuntimeException(
        "Insufficient funds in account $accountId. Current balance: $currentBalance, Requested: $requestedAmount",
    )
