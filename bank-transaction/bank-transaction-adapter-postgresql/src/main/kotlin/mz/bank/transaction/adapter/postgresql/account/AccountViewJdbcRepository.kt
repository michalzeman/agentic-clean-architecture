package mz.bank.transaction.adapter.postgresql.account

import org.springframework.data.repository.ListCrudRepository
import java.util.UUID

/**
 * Spring Data JDBC repository for PostgresAccountView.
 * Provides CRUD operations and a lookup by domain account_id.
 */
internal interface AccountViewJdbcRepository : ListCrudRepository<PostgresAccountView, Long> {
    fun findByAccountId(accountId: UUID): PostgresAccountView?
}
