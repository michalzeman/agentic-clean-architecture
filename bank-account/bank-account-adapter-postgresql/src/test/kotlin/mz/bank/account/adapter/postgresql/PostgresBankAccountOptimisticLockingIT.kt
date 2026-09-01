package mz.bank.account.adapter.postgresql

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
    excludeFilters = [ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [BankAccountDomainEventListener::class])],
)
@ActiveProfiles("postgres-persistence")
private class EntityTestApplication

/**
 * Integration tests proving the optimistic-lock guard on bank_account.
 *
 * Uses a real PostgreSQL via Testcontainers to exercise the SQL {@code WHERE version = ?}
 * that Spring Data JDBC generates for @Version-annotated entities.
 */
@SpringBootTest(classes = [EntityTestApplication::class])
@Testcontainers
class PostgresBankAccountOptimisticLockingIT {
    @Autowired
    private lateinit var repository: BankAccountJdbcRepository

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
    fun `should save single-event aggregate and write aggregate_version as-is`() {
        // Given — insert a bank account at domain version V=1
        val aggregateId = UUID.randomUUID()
        val now = Instant.now()
        val initial =
            PostgresBankAccount(
                aggregateId = aggregateId,
                email = "test-${UUID.randomUUID()}@example.com",
                amount = BigDecimal("1000.00"),
                aggregateVersion = 1L,
                createdAt = now,
                updatedAt = now,
            )
        val saved = repository.save(initial)

        // When — single-event update: domain version advanced by +1 (V1→V2), version token carried
        val updated =
            PostgresBankAccount(
                id = saved.id,
                aggregateId = aggregateId,
                email = saved.email,
                amount = BigDecimal("900.00"),
                aggregateVersion = 2L,
                version = saved.version,
                createdAt = now,
                updatedAt = Instant.now(),
            )
        val result = repository.save(updated)

        // Then — aggregate_version is domain's V2 (written as-is), version token advanced by exactly +1
        assertThat(result.aggregateVersion).isEqualTo(2L)
        assertThat(result.version).isEqualTo(saved.version + 1)
    }

    @Test
    fun `should throw OptimisticLockingFailureException when version token is stale`() {
        // Given — insert a bank account
        val aggregateId = UUID.randomUUID()
        val now = Instant.now()
        val initial =
            PostgresBankAccount(
                aggregateId = aggregateId,
                email = "lock-${UUID.randomUUID()}@example.com",
                amount = BigDecimal("500.00"),
                aggregateVersion = 1L,
                createdAt = now,
                updatedAt = now,
            )
        val saved = repository.save(initial)

        // When — advance the token behind the repository's back (simulate concurrent update)
        val concurrentUpdate =
            PostgresBankAccount(
                id = saved.id,
                aggregateId = aggregateId,
                email = saved.email,
                amount = BigDecimal("400.00"),
                aggregateVersion = 2L,
                version = saved.version,
                createdAt = now,
                updatedAt = Instant.now(),
            )
        repository.save(concurrentUpdate)

        // Then — attempt to save with the first loaded token → stale token → exception
        val staleUpdate =
            PostgresBankAccount(
                id = saved.id,
                aggregateId = aggregateId,
                email = saved.email,
                amount = BigDecimal("300.00"),
                aggregateVersion = 3L,
                version = saved.version,
                createdAt = now,
                updatedAt = Instant.now(),
            )
        assertThatThrownBy { repository.save(staleUpdate) }
            .isInstanceOf(OptimisticLockingFailureException::class.java)
    }
}
