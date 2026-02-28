package mz.bank.transaction.adapter.postgresql

import kotlinx.coroutines.runBlocking
import mz.bank.transaction.domain.BankTransaction
import mz.bank.transaction.domain.BankTransactionAggregate
import mz.bank.transaction.domain.BankTransactionEvent
import mz.bank.transaction.domain.BankTransactionStatus
import mz.shared.domain.AggregateId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class PostgresBankTransactionRepositoryTest {
    private lateinit var bankTransactionJdbcRepository: BankTransactionJdbcRepository
    private lateinit var postgresBankTransactionRepository: PostgresBankTransactionRepository

    @BeforeEach
    fun setUp() {
        bankTransactionJdbcRepository = mock()
        postgresBankTransactionRepository = PostgresBankTransactionRepository(bankTransactionJdbcRepository)
    }

    // ==================== findById Tests ====================

    @Test
    fun `should find bankTransaction by aggregateId when it exists`(): Unit =
        runBlocking {
            // Given
            val id = UUID.randomUUID()
            val aggregateId = AggregateId(id.toString())
            val fromAccountId = UUID.randomUUID()
            val toAccountId = UUID.randomUUID()
            val now = Instant.now()
            val postgresBankTransaction =
                PostgresBankTransaction(
                    id = 1L,
                    aggregateId = id,
                    correlationId = "corr-001",
                    fromAccountId = fromAccountId,
                    toAccountId = toAccountId,
                    amount = BigDecimal("500.00"),
                    moneyWithdrawn = true,
                    moneyDeposited = false,
                    status = BankTransactionStatus.CREATED.name,
                    version = 1L,
                    createdAt = now,
                    updatedAt = now,
                )
            whenever(bankTransactionJdbcRepository.findByAggregateId(id)).thenReturn(postgresBankTransaction)

            // When
            val result = postgresBankTransactionRepository.findById(aggregateId)

            // Then
            assertThat(result).isNotNull
            assertThat(result?.aggregateId).isEqualTo(aggregateId)
            assertThat(result?.correlationId).isEqualTo("corr-001")
            assertThat(result?.amount).isEqualByComparingTo(BigDecimal("500.00"))
            assertThat(result?.moneyWithdrawn).isTrue()
            assertThat(result?.moneyDeposited).isFalse()
            assertThat(result?.status).isEqualTo(BankTransactionStatus.CREATED)
            verify(bankTransactionJdbcRepository).findByAggregateId(id)
        }

    @Test
    fun `should return null when bankTransaction does not exist`(): Unit =
        runBlocking {
            // Given
            val id = UUID.randomUUID()
            val aggregateId = AggregateId(id.toString())
            whenever(bankTransactionJdbcRepository.findByAggregateId(id)).thenReturn(null)

            // When
            val result = postgresBankTransactionRepository.findById(aggregateId)

            // Then
            assertThat(result).isNull()
            verify(bankTransactionJdbcRepository).findByAggregateId(id)
        }

    // ==================== upsert Tests ====================

    @Test
    fun `should insert bankTransaction when it does not exist yet`(): Unit =
        runBlocking {
            // Given
            val aggregateId = AggregateId(UUID.randomUUID().toString())
            val uuid = UUID.fromString(aggregateId.value)
            val fromAccountUUID = UUID.randomUUID()
            val toAccountUUID = UUID.randomUUID()
            val now = Instant.now()
            val bankTransaction =
                BankTransaction(
                    aggregateId = aggregateId,
                    correlationId = "corr-new",
                    fromAccountId = AggregateId(fromAccountUUID.toString()),
                    toAccountId = AggregateId(toAccountUUID.toString()),
                    amount = BigDecimal("750.00"),
                    moneyWithdrawn = false,
                    moneyDeposited = false,
                    status = BankTransactionStatus.CREATED,
                    version = 0L,
                    createdAt = now,
                    updatedAt = now,
                )
            val aggregate = BankTransactionAggregate(bankTransaction, emptyList())
            val savedEntity =
                PostgresBankTransaction(
                    id = 1L,
                    aggregateId = uuid,
                    correlationId = "corr-new",
                    fromAccountId = fromAccountUUID,
                    toAccountId = toAccountUUID,
                    amount = BigDecimal("750.00"),
                    moneyWithdrawn = false,
                    moneyDeposited = false,
                    status = BankTransactionStatus.CREATED.name,
                    version = 0L,
                    createdAt = now,
                    updatedAt = now,
                )
            whenever(bankTransactionJdbcRepository.findByAggregateId(uuid)).thenReturn(null)
            whenever(bankTransactionJdbcRepository.save(any())).thenReturn(savedEntity)

            // When
            val result = postgresBankTransactionRepository.upsert(aggregate)

            // Then
            assertThat(result.aggregateId).isEqualTo(aggregateId)
            assertThat(result.correlationId).isEqualTo("corr-new")
            assertThat(result.amount).isEqualByComparingTo(BigDecimal("750.00"))
            assertThat(result.status).isEqualTo(BankTransactionStatus.CREATED)
            verify(bankTransactionJdbcRepository).findByAggregateId(uuid)
            verify(bankTransactionJdbcRepository).save(any())
        }

    @Test
    fun `should update bankTransaction when it already exists`(): Unit =
        runBlocking {
            // Given
            val aggregateId = AggregateId(UUID.randomUUID().toString())
            val uuid = UUID.fromString(aggregateId.value)
            val fromAccountUUID = UUID.randomUUID()
            val toAccountUUID = UUID.randomUUID()
            val now = Instant.now()
            val bankTransaction =
                BankTransaction(
                    aggregateId = aggregateId,
                    correlationId = "corr-existing",
                    fromAccountId = AggregateId(fromAccountUUID.toString()),
                    toAccountId = AggregateId(toAccountUUID.toString()),
                    amount = BigDecimal("1000.00"),
                    moneyWithdrawn = true,
                    moneyDeposited = true,
                    status = BankTransactionStatus.FINISHED,
                    version = 5L,
                    createdAt = now.minusSeconds(3600),
                    updatedAt = now,
                )
            val aggregate = BankTransactionAggregate(bankTransaction, emptyList())
            val existingEntity =
                PostgresBankTransaction(
                    id = 3L,
                    aggregateId = uuid,
                    correlationId = "corr-existing",
                    fromAccountId = fromAccountUUID,
                    toAccountId = toAccountUUID,
                    amount = BigDecimal("1000.00"),
                    moneyWithdrawn = false,
                    moneyDeposited = false,
                    status = BankTransactionStatus.CREATED.name,
                    version = 2L,
                    createdAt = now.minusSeconds(3600),
                    updatedAt = now.minusSeconds(100),
                )
            val savedEntity =
                PostgresBankTransaction(
                    id = 3L,
                    aggregateId = uuid,
                    correlationId = "corr-existing",
                    fromAccountId = fromAccountUUID,
                    toAccountId = toAccountUUID,
                    amount = BigDecimal("1000.00"),
                    moneyWithdrawn = true,
                    moneyDeposited = true,
                    status = BankTransactionStatus.FINISHED.name,
                    version = 5L,
                    createdAt = now.minusSeconds(3600),
                    updatedAt = now,
                )
            whenever(bankTransactionJdbcRepository.findByAggregateId(uuid)).thenReturn(existingEntity)
            whenever(bankTransactionJdbcRepository.save(any())).thenReturn(savedEntity)

            // When
            val result = postgresBankTransactionRepository.upsert(aggregate)

            // Then
            assertThat(result.aggregateId).isEqualTo(aggregateId)
            assertThat(result.status).isEqualTo(BankTransactionStatus.FINISHED)
            assertThat(result.moneyWithdrawn).isTrue()
            assertThat(result.moneyDeposited).isTrue()
            assertThat(result.version).isEqualTo(5L)
            verify(bankTransactionJdbcRepository).save(any())
        }

    @Test
    fun `should preserve domain events when upserting`(): Unit =
        runBlocking {
            // Given
            val aggregateId = AggregateId(UUID.randomUUID().toString())
            val uuid = UUID.fromString(aggregateId.value)
            val fromAccountUUID = UUID.randomUUID()
            val toAccountUUID = UUID.randomUUID()
            val now = Instant.now()
            val bankTransaction =
                BankTransaction(
                    aggregateId = aggregateId,
                    correlationId = "corr-events",
                    fromAccountId = AggregateId(fromAccountUUID.toString()),
                    toAccountId = AggregateId(toAccountUUID.toString()),
                    amount = BigDecimal("300.00"),
                    moneyWithdrawn = true,
                    moneyDeposited = false,
                    status = BankTransactionStatus.CREATED,
                    version = 1L,
                    createdAt = now,
                    updatedAt = now,
                )
            val domainEvents =
                listOf(
                    BankTransactionEvent.BankTransactionMoneyWithdrawn(
                        aggregateId = aggregateId,
                        correlationId = "corr-events",
                        updatedAt = now,
                        accountId = AggregateId("acc-from"),
                        toAccountId = AggregateId("acc-to"),
                        amount = BigDecimal("300.00"),
                    ),
                )
            val aggregate = BankTransactionAggregate(bankTransaction, domainEvents)
            val savedEntity =
                PostgresBankTransaction(
                    id = 1L,
                    aggregateId = uuid,
                    correlationId = "corr-events",
                    fromAccountId = fromAccountUUID,
                    toAccountId = toAccountUUID,
                    amount = BigDecimal("300.00"),
                    moneyWithdrawn = true,
                    moneyDeposited = false,
                    status = BankTransactionStatus.CREATED.name,
                    version = 1L,
                    createdAt = now,
                    updatedAt = now,
                )
            whenever(bankTransactionJdbcRepository.findByAggregateId(uuid)).thenReturn(null)
            whenever(bankTransactionJdbcRepository.save(any())).thenReturn(savedEntity)

            // When
            val result = postgresBankTransactionRepository.upsert(aggregate)

            // Then
            assertThat(result.moneyWithdrawn).isTrue()
            verify(bankTransactionJdbcRepository).save(any())
        }
}
