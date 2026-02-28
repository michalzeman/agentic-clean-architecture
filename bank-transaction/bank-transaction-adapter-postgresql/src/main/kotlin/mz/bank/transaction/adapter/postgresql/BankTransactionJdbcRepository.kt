package mz.bank.transaction.adapter.postgresql

import org.springframework.data.repository.ListCrudRepository
import java.util.UUID

/**
 * Spring Data JDBC repository for PostgresBankTransaction.
 * Provides CRUD operations and a lookup by domain aggregate_id.
 */
internal interface BankTransactionJdbcRepository : ListCrudRepository<PostgresBankTransaction, Long> {
    fun findByAggregateId(aggregateId: UUID): PostgresBankTransaction?
}
