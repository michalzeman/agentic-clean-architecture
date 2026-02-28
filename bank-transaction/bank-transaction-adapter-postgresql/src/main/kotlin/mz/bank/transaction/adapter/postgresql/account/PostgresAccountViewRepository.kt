package mz.bank.transaction.adapter.postgresql.account

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mz.bank.transaction.application.account.AccountViewRepository
import mz.bank.transaction.domain.account.AccountView
import mz.shared.domain.AggregateId
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * PostgreSQL implementation of AccountViewRepository using Spring Data JDBC.
 * Resolves the surrogate id by account_id before each save so that
 * Spring Data JDBC issues UPDATE (non-null id) vs INSERT (null id) correctly.
 * Active when the 'postgres-persistence' Spring profile is enabled.
 */
@Component
@Profile("postgres-persistence")
internal class PostgresAccountViewRepository(
    private val repository: AccountViewJdbcRepository,
) : AccountViewRepository {
    override suspend fun findById(accountId: AggregateId): AccountView? =
        withContext(Dispatchers.IO) {
            repository
                .findByAccountId(UUID.fromString(accountId.value))
                ?.toAccountView()
        }

    override suspend fun upsert(accountView: AccountView): AccountView =
        withContext(Dispatchers.IO) {
            val existingId =
                repository
                    .findByAccountId(UUID.fromString(accountView.accountId.value))
                    ?.id
            repository.save(accountView.toPostgresAccountView(existingId)).toAccountView()
        }
}
