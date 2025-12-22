package mz.bank.transaction.application.account

import mz.bank.transaction.domain.account.AccountView
import mz.shared.domain.AggregateId

/**
 * Repository interface for AccountView persistence operations.
 * Abstracts persistence details for the account view read model.
 */
interface AccountViewRepository {
    /**
     * Finds an AccountView by its account ID.
     *
     * @return The AccountView if found, null otherwise
     */
    suspend fun findById(accountId: AggregateId): AccountView?

    /**
     * Inserts or updates an AccountView.
     * This is a simple upsert operation without event publishing since this is just a read model.
     *
     * @param accountView The account view to persist
     * @return The persisted AccountView
     */
    suspend fun upsert(accountView: AccountView): AccountView
}
