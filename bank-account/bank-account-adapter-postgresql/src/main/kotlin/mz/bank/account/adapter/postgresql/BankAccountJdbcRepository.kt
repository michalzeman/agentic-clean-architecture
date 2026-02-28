package mz.bank.account.adapter.postgresql

import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.ListCrudRepository
import java.util.UUID

/**
 * Spring Data JDBC repository for BankAccount persistence.
 * Uses the surrogate Long PK internally; domain lookups go via aggregate_id.
 */
internal interface BankAccountJdbcRepository : ListCrudRepository<PostgresBankAccount, Long> {
    @Query("SELECT * FROM bank_account WHERE aggregate_id = :aggregateId")
    fun findByAggregateId(aggregateId: UUID): PostgresBankAccount?

    @Query("SELECT COUNT(*) > 0 FROM bank_account WHERE email = :email")
    fun existsByEmail(email: String): Boolean
}
