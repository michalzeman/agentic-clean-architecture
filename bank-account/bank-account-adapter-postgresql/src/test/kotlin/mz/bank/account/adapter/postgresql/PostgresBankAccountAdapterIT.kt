package mz.bank.account.adapter.postgresql

import kotlinx.coroutines.runBlocking
import mz.bank.account.domain.BankAccount
import mz.bank.account.domain.BankAccountAggregate
import mz.bank.account.domain.Email
import mz.shared.domain.AggregateId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType
import org.springframework.context.annotation.Import
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
@Import(PostgresBankAccountRepository::class)
private class AdapterTestApplication

/**
 * Adapter-level integration tests: saves go through PostgresBankAccountRepository.upsert(),
 * which is where the original domainVersion - 1 bug lived. A regression in the adapter wiring
 * would be caught here — unlike the entity-level tests that use the JDBC repository directly.
 */
@SpringBootTest(classes = [AdapterTestApplication::class])
@Testcontainers
@ActiveProfiles("postgres-persistence")
class PostgresBankAccountAdapterIT {
    @Autowired
    private lateinit var adapter: PostgresBankAccountRepository

    @Autowired
    private lateinit var jdbcRepository: BankAccountJdbcRepository

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
    fun `should upsert single-event aggregate through adapter and write aggregate_version as-is`(): Unit =
        runBlocking {
            // Given — insert at domain version V=1
            val aggregateId = AggregateId(UUID.randomUUID().toString())
            val now = Instant.now()
            val initial =
                BankAccount(
                    aggregateId = aggregateId,
                    email = Email("adapter-${UUID.randomUUID()}@example.com"),
                    amount = BigDecimal("1000.00"),
                    version = 1L,
                    createdAt = now,
                    updatedAt = now,
                )
            adapter.upsert(BankAccountAggregate(initial, emptyList()))

            // When — single-event update: domain version V=1→V=2
            val updated = initial.copy(version = 2L, amount = BigDecimal("900.00"), updatedAt = Instant.now())
            val result = adapter.upsert(BankAccountAggregate(updated, emptyList()))

            // Then — domain sees aggregate_version as-is
            assertThat(result.version).isEqualTo(2L)

            // And the DB row: aggregate_version=2, version token advanced by exactly +1
            val row = jdbcRepository.findByAggregateId(UUID.fromString(aggregateId.value))!!
            assertThat(row.aggregateVersion).isEqualTo(2L)
            assertThat(row.version).isEqualTo(2L) // INSERT→1, UPDATE→2
        }

    @Test
    fun `should advance version token on sequential adapter upserts`(): Unit =
        runBlocking {
            // Given — insert at V=1
            val aggregateId = AggregateId(UUID.randomUUID().toString())
            val now = Instant.now()
            val t1 =
                BankAccount(
                    aggregateId = aggregateId,
                    email = Email("seq-${UUID.randomUUID()}@example.com"),
                    amount = BigDecimal("1000.00"),
                    version = 1L,
                    createdAt = now,
                    updatedAt = now,
                )
            adapter.upsert(BankAccountAggregate(t1, emptyList()))

            // When — two sequential updates through the adapter
            val t2 = t1.copy(version = 2L, amount = BigDecimal("900.00"), updatedAt = Instant.now())
            adapter.upsert(BankAccountAggregate(t2, emptyList()))

            val t3 = t2.copy(version = 3L, amount = BigDecimal("800.00"), updatedAt = Instant.now())
            adapter.upsert(BankAccountAggregate(t3, emptyList()))

            // Then — aggregate_version=3, version token=3 (three saves)
            val row = jdbcRepository.findByAggregateId(UUID.fromString(aggregateId.value))!!
            assertThat(row.aggregateVersion).isEqualTo(3L)
            assertThat(row.version).isEqualTo(3L)
        }
}
