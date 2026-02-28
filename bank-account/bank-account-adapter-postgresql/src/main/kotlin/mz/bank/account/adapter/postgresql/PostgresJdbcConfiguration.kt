package mz.bank.account.adapter.postgresql

import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.jdbc.repository.config.AbstractJdbcConfiguration
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories

/**
 * Spring Data JDBC configuration for the bank-account PostgreSQL adapter.
 * Active only when the 'postgres-persistence' Spring profile is enabled.
 * Extends AbstractJdbcConfiguration to properly integrate with Spring Data JDBC
 * infrastructure when both JDBC and Redis modules are on the classpath.
 * No custom converters are needed: Set<String> JSONB fields are handled by
 * explicit JSON serialization in the PostgresBankAccount mapping functions.
 */
@Configuration
@Profile("postgres-persistence")
@EnableJdbcRepositories(basePackageClasses = [BankAccountJdbcRepository::class])
class PostgresJdbcConfiguration : AbstractJdbcConfiguration()
