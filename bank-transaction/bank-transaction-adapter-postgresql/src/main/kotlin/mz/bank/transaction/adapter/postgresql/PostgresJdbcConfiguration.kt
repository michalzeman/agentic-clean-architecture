package mz.bank.transaction.adapter.postgresql

import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.jdbc.repository.config.AbstractJdbcConfiguration
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories

/**
 * Spring Data JDBC configuration for the bank-transaction PostgreSQL adapter.
 * Active only when the 'postgres-persistence' Spring profile is enabled.
 * Extends AbstractJdbcConfiguration to properly integrate with Spring Data JDBC
 * infrastructure when both JDBC and Redis modules are on the classpath.
 */
@Configuration
@Profile("postgres-persistence")
@EnableJdbcRepositories(
    basePackageClasses = [BankTransactionJdbcRepository::class],
)
class PostgresJdbcConfiguration : AbstractJdbcConfiguration()
