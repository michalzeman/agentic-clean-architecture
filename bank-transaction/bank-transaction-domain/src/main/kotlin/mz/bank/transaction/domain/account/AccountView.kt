package mz.bank.transaction.domain.account

import mz.shared.domain.AggregateId

/**
 * AccountView is a local read model in the bank-transaction bounded context.
 * It stores minimal account information received from the bank-account service via Redis streams.
 * This view is used for local queries and validation within the transaction context.
 */
data class AccountView(
    val accountId: AggregateId,
)
