package mz.bank.transaction.adapter.postgresql

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@SpringBootApplication
@ComponentScan(
    excludeFilters = [ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [BankTransactionDomainEventListener::class])],
)
@ActiveProfiles("postgres-persistence")
private class EntityTestApplication

/**
 * Integration tests proving the optimistic-lock guard on bank_transaction.
 *
 * Uses a real PostgreSQL via Testcontainers to exercise the SQL {@code WHERE version = ?}
 * that Spring Data JDBC generates for @Version-annotated entities.
 */
@SpringBootTest(classes = [EntityTestApplication::class])
@Testcontainers
class PostgresBankTransactionOptimisticLockingIT {
    @Autowired
    private lateinit var repository: BankTransactionJdbcRepository

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> =
            PostgreSQLContainer("postgres:16-alpine")
                .withDatabaseName("bank_db")
                .withUsername("bank_user")
                .withPassword("bank_pass")

        @DynamicPropertySource
        @JvmStatic
        fun registerProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
        }
    }

    @Test
    fun `should save multi-event aggregate and write aggregate_version as-is`() {
        // Given — insert a bank transaction at domain version V=2 (moneyWithdrawn + moneyDeposited)
        val aggregateId = UUID.randomUUID()
        val now = Instant.now()
        val initial =
            PostgresBankTransaction(
                aggregateId = aggregateId,
                correlationId = "corr-001",
                fromAccountId = UUID.randomUUID(),
                toAccountId = UUID.randomUUID(),
                amount = BigDecimal("500.00"),
                moneyWithdrawn = true,
                moneyDeposited = true,
                withdrawCompensated = false,
                depositCompensated = false,
                status = "CREATED",
                aggregateVersion = 2L,
                createdAt = now,
                updatedAt = now,
            )
        val saved = repository.save(initial)

        // When — cancel scenario: domain version advanced by +3 (V2→V5), version token carried from existing
        val updated =
            PostgresBankTransaction(
                id = saved.id,
                aggregateId = aggregateId,
                correlationId = "corr-001",
                fromAccountId = saved.fromAccountId,
                toAccountId = saved.toAccountId,
                amount = BigDecimal("500.00"),
                moneyWithdrawn = false,
                moneyDeposited = false,
                withdrawCompensated = true,
                depositCompensated = true,
                status = "FAILED",
                aggregateVersion = 5L,
                version = saved.version,
                createdAt = now,
                updatedAt = Instant.now(),
            )
        val result = repository.save(updated)

        // Then — aggregate_version is domain's V5 (written as-is), version token advanced by exactly +1
        assertThat(result.aggregateVersion).isEqualTo(5L)
        assertThat(result.version).isEqualTo(saved.version + 1)
    }

    @Test
    fun `should throw OptimisticLockingFailureException when version token is stale`() {
        // Given — insert a bank transaction
        val aggregateId = UUID.randomUUID()
        val now = Instant.now()
        val initial =
            PostgresBankTransaction(
                aggregateId = aggregateId,
                correlationId = "corr-002",
                fromAccountId = UUID.randomUUID(),
                toAccountId = UUID.randomUUID(),
                amount = BigDecimal("300.00"),
                moneyWithdrawn = false,
                moneyDeposited = false,
                withdrawCompensated = false,
                depositCompensated = false,
                status = "CREATED",
                aggregateVersion = 1L,
                createdAt = now,
                updatedAt = now,
            )
        val saved = repository.save(initial)

        // When — advance the token behind the repository's back (simulate concurrent update)
        val concurrentUpdate =
            PostgresBankTransaction(
                id = saved.id,
                aggregateId = aggregateId,
                correlationId = "corr-002",
                fromAccountId = saved.fromAccountId,
                toAccountId = saved.toAccountId,
                amount = BigDecimal("300.00"),
                moneyWithdrawn = true,
                moneyDeposited = false,
                withdrawCompensated = false,
                depositCompensated = false,
                status = "CREATED",
                aggregateVersion = 2L,
                version = saved.version,
                createdAt = now,
                updatedAt = Instant.now(),
            )
        repository.save(concurrentUpdate)

        // Then — attempt to save with the first loaded token → stale token → exception
        val staleUpdate =
            PostgresBankTransaction(
                id = saved.id,
                aggregateId = aggregateId,
                correlationId = "corr-002",
                fromAccountId = saved.fromAccountId,
                toAccountId = saved.toAccountId,
                amount = BigDecimal("300.00"),
                moneyWithdrawn = true,
                moneyDeposited = true,
                withdrawCompensated = false,
                depositCompensated = false,
                status = "CREATED",
                aggregateVersion = 3L,
                version = saved.version,
                createdAt = now,
                updatedAt = Instant.now(),
            )
        assertThatThrownBy { repository.save(staleUpdate) }
            .isInstanceOf(OptimisticLockingFailureException::class.java)
    }
}
