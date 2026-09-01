package mz.bank.transaction.adapter.postgresql

import kotlinx.coroutines.runBlocking
import mz.bank.transaction.domain.BankTransaction
import mz.bank.transaction.domain.BankTransactionAggregate
import mz.bank.transaction.domain.BankTransactionStatus
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
    excludeFilters = [ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [BankTransactionDomainEventListener::class])],
)
@Import(PostgresBankTransactionRepository::class)
private class AdapterTestApplication

/**
 * Adapter-level integration tests: saves go through PostgresBankTransactionRepository.upsert(),
 * which is where the original domainVersion - 1 bug lived. A regression in the adapter wiring
 * would be caught here — unlike the entity-level tests that use the JDBC repository directly.
 */
@SpringBootTest(classes = [AdapterTestApplication::class])
@Testcontainers
@ActiveProfiles("postgres-persistence")
class PostgresBankTransactionAdapterIT {
    @Autowired
    private lateinit var adapter: PostgresBankTransactionRepository

    @Autowired
    private lateinit var jdbcRepository: BankTransactionJdbcRepository

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
    fun `should upsert multi-event aggregate through adapter and write aggregate_version as-is`(): Unit =
        runBlocking {
            // Given — insert at domain version V=2 (moneyWithdrawn + moneyDeposited)
            val aggregateId = AggregateId(UUID.randomUUID().toString())
            val fromAccountId = AggregateId(UUID.randomUUID().toString())
            val toAccountId = AggregateId(UUID.randomUUID().toString())
            val now = Instant.now()
            val initial =
                BankTransaction(
                    aggregateId = aggregateId,
                    correlationId = "corr-adapter-001",
                    fromAccountId = fromAccountId,
                    toAccountId = toAccountId,
                    amount = BigDecimal("500.00"),
                    moneyWithdrawn = true,
                    moneyDeposited = true,
                    status = BankTransactionStatus.CREATED,
                    version = 2L,
                    createdAt = now,
                    updatedAt = now,
                )
            adapter.upsert(BankTransactionAggregate(initial, emptyList()))

            // When — cancel scenario: domain version advanced by +3 (V2→V5)
            val cancel =
                BankTransaction(
                    aggregateId = aggregateId,
                    correlationId = "corr-adapter-001",
                    fromAccountId = fromAccountId,
                    toAccountId = toAccountId,
                    amount = BigDecimal("500.00"),
                    moneyWithdrawn = false,
                    moneyDeposited = false,
                    withdrawCompensated = true,
                    depositCompensated = true,
                    status = BankTransactionStatus.FAILED,
                    version = 5L,
                    createdAt = now,
                    updatedAt = Instant.now(),
                )
            val result = adapter.upsert(BankTransactionAggregate(cancel, emptyList()))

            // Then — domain sees aggregate_version as-is
            assertThat(result.version).isEqualTo(5L)

            // And the DB row: aggregate_version=5, version token advanced by exactly +1 from first save
            val row = jdbcRepository.findByAggregateId(UUID.fromString(aggregateId.value))!!
            assertThat(row.aggregateVersion).isEqualTo(5L)
            assertThat(row.version).isEqualTo(2L) // INSERT→1, UPDATE→2
        }

    @Test
    fun `should advance version token on sequential adapter upserts`(): Unit =
        runBlocking {
            // Given — insert at V=1
            val aggregateId = AggregateId(UUID.randomUUID().toString())
            val fromAccountId = AggregateId(UUID.randomUUID().toString())
            val toAccountId = AggregateId(UUID.randomUUID().toString())
            val now = Instant.now()
            val t1 =
                BankTransaction(
                    aggregateId = aggregateId,
                    correlationId = "corr-seq-001",
                    fromAccountId = fromAccountId,
                    toAccountId = toAccountId,
                    amount = BigDecimal("100.00"),
                    status = BankTransactionStatus.CREATED,
                    version = 1L,
                    createdAt = now,
                    updatedAt = now,
                )
            adapter.upsert(BankTransactionAggregate(t1, emptyList()))

            // When — two sequential updates through the adapter
            val t2 = t1.copy(version = 2L, moneyWithdrawn = true, updatedAt = Instant.now())
            adapter.upsert(BankTransactionAggregate(t2, emptyList()))

            val t3 = t2.copy(version = 3L, moneyDeposited = true, updatedAt = Instant.now())
            adapter.upsert(BankTransactionAggregate(t3, emptyList()))

            // Then — aggregate_version=3, version token=3 (three saves)
            val row = jdbcRepository.findByAggregateId(UUID.fromString(aggregateId.value))!!
            assertThat(row.aggregateVersion).isEqualTo(3L)
            assertThat(row.version).isEqualTo(3L)
        }
}
