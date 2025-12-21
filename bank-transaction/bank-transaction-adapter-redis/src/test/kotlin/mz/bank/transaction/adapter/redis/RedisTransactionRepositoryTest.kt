package mz.bank.transaction.adapter.redis

import kotlinx.coroutines.runBlocking
import mz.bank.transaction.domain.Transaction
import mz.bank.transaction.domain.TransactionAggregate
import mz.bank.transaction.domain.TransactionEvent
import mz.bank.transaction.domain.TransactionStatus
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
import java.util.Optional
import java.util.UUID

class RedisTransactionRepositoryTest {
    private lateinit var transactionDataRepository: TransactionDataRepository
    private lateinit var redisTransactionRepository: RedisTransactionRepository

    @BeforeEach
    fun setUp() {
        transactionDataRepository = mock()
        redisTransactionRepository = RedisTransactionRepository(transactionDataRepository)
    }

    // ==================== findById Tests ====================

    @Test
    fun `should find transaction by id when it exists`(): Unit =
        runBlocking {
            // Given
            val id = UUID.randomUUID()
            val aggregateId = AggregateId(id.toString())
            val now = Instant.now()
            val redisTransaction =
                RedisTransaction(
                    id = id,
                    correlationId = "corr-001",
                    fromAccountId = "acc-001",
                    toAccountId = "acc-002",
                    amount = BigDecimal("500.00"),
                    moneyWithdrawn = true,
                    moneyDeposited = false,
                    status = TransactionStatus.CREATED.name,
                    version = 1L,
                    createdAt = now,
                    updatedAt = now,
                )

            whenever(transactionDataRepository.findById(id)).thenReturn(Optional.of(redisTransaction))

            // When
            val result = redisTransactionRepository.findById(aggregateId)

            // Then
            assertThat(result).isNotNull
            assertThat(result?.aggregateId).isEqualTo(aggregateId)
            assertThat(result?.correlationId).isEqualTo("corr-001")
            assertThat(result?.amount).isEqualByComparingTo(BigDecimal("500.00"))
            assertThat(result?.moneyWithdrawn).isTrue()
            assertThat(result?.moneyDeposited).isFalse()
            assertThat(result?.status).isEqualTo(TransactionStatus.CREATED)
            verify(transactionDataRepository).findById(id)
        }

    @Test
    fun `should return null when transaction does not exist`(): Unit =
        runBlocking {
            // Given
            val id = UUID.randomUUID()
            val aggregateId = AggregateId(id.toString())

            whenever(transactionDataRepository.findById(id)).thenReturn(Optional.empty())

            // When
            val result = redisTransactionRepository.findById(aggregateId)

            // Then
            assertThat(result).isNull()
            verify(transactionDataRepository).findById(id)
        }

    // ==================== upsert Tests ====================

    @Test
    fun `should upsert transaction aggregate successfully`(): Unit =
        runBlocking {
            // Given
            val aggregateId = AggregateId(UUID.randomUUID().toString())
            val now = Instant.now()
            val transaction =
                Transaction(
                    aggregateId = aggregateId,
                    correlationId = "corr-upsert",
                    fromAccountId = AggregateId("acc-from"),
                    toAccountId = AggregateId("acc-to"),
                    amount = BigDecimal("750.00"),
                    moneyWithdrawn = false,
                    moneyDeposited = false,
                    status = TransactionStatus.CREATED,
                    version = 0L,
                    createdAt = now,
                    updatedAt = now,
                )
            val aggregate = TransactionAggregate(transaction, emptyList())

            val savedRedisTransaction =
                RedisTransaction(
                    id = UUID.fromString(aggregateId.value),
                    correlationId = "corr-upsert",
                    fromAccountId = "acc-from",
                    toAccountId = "acc-to",
                    amount = BigDecimal("750.00"),
                    moneyWithdrawn = false,
                    moneyDeposited = false,
                    status = TransactionStatus.CREATED.name,
                    version = 0L,
                    createdAt = now,
                    updatedAt = now,
                )

            whenever(transactionDataRepository.save(any())).thenReturn(savedRedisTransaction)

            // When
            val result = redisTransactionRepository.upsert(aggregate)

            // Then
            assertThat(result.aggregateId).isEqualTo(aggregateId)
            assertThat(result.correlationId).isEqualTo("corr-upsert")
            assertThat(result.amount).isEqualByComparingTo(BigDecimal("750.00"))
            assertThat(result.status).isEqualTo(TransactionStatus.CREATED)
            verify(transactionDataRepository).save(any())
        }

    @Test
    fun `should preserve domain events when upserting`(): Unit =
        runBlocking {
            // Given
            val aggregateId = AggregateId(UUID.randomUUID().toString())
            val now = Instant.now()
            val transaction =
                Transaction(
                    aggregateId = aggregateId,
                    correlationId = "corr-events",
                    fromAccountId = AggregateId("acc-from"),
                    toAccountId = AggregateId("acc-to"),
                    amount = BigDecimal("300.00"),
                    moneyWithdrawn = true,
                    moneyDeposited = false,
                    status = TransactionStatus.CREATED,
                    version = 1L,
                    createdAt = now,
                    updatedAt = now,
                )
            val domainEvents =
                listOf(
                    TransactionEvent.TransactionMoneyWithdrawn(
                        aggregateId = aggregateId,
                        correlationId = "corr-events",
                        updatedAt = now,
                    ),
                )
            val aggregate = TransactionAggregate(transaction, domainEvents)

            val savedRedisTransaction =
                RedisTransaction(
                    id = UUID.fromString(aggregateId.value),
                    correlationId = "corr-events",
                    fromAccountId = "acc-from",
                    toAccountId = "acc-to",
                    amount = BigDecimal("300.00"),
                    moneyWithdrawn = true,
                    moneyDeposited = false,
                    status = TransactionStatus.CREATED.name,
                    version = 1L,
                    createdAt = now,
                    updatedAt = now,
                )

            whenever(transactionDataRepository.save(any())).thenReturn(savedRedisTransaction)

            // When
            val result = redisTransactionRepository.upsert(aggregate)

            // Then
            assertThat(result.moneyWithdrawn).isTrue()
            verify(transactionDataRepository).save(any())
        }

    @Test
    fun `should handle upsert with FINISHED status`(): Unit =
        runBlocking {
            // Given
            val aggregateId = AggregateId(UUID.randomUUID().toString())
            val now = Instant.now()
            val transaction =
                Transaction(
                    aggregateId = aggregateId,
                    correlationId = "corr-finished",
                    fromAccountId = AggregateId("acc-from"),
                    toAccountId = AggregateId("acc-to"),
                    amount = BigDecimal("1000.00"),
                    moneyWithdrawn = true,
                    moneyDeposited = true,
                    status = TransactionStatus.FINISHED,
                    version = 5L,
                    createdAt = now.minusSeconds(3600),
                    updatedAt = now,
                )
            val aggregate = TransactionAggregate(transaction, emptyList())

            val savedRedisTransaction =
                RedisTransaction(
                    id = UUID.fromString(aggregateId.value),
                    correlationId = "corr-finished",
                    fromAccountId = "acc-from",
                    toAccountId = "acc-to",
                    amount = BigDecimal("1000.00"),
                    moneyWithdrawn = true,
                    moneyDeposited = true,
                    status = TransactionStatus.FINISHED.name,
                    version = 5L,
                    createdAt = now.minusSeconds(3600),
                    updatedAt = now,
                )

            whenever(transactionDataRepository.save(any())).thenReturn(savedRedisTransaction)

            // When
            val result = redisTransactionRepository.upsert(aggregate)

            // Then
            assertThat(result.status).isEqualTo(TransactionStatus.FINISHED)
            assertThat(result.moneyWithdrawn).isTrue()
            assertThat(result.moneyDeposited).isTrue()
            assertThat(result.version).isEqualTo(5L)
            verify(transactionDataRepository).save(any())
        }

    @Test
    fun `should handle upsert with FAILED status`(): Unit =
        runBlocking {
            // Given
            val aggregateId = AggregateId(UUID.randomUUID().toString())
            val now = Instant.now()
            val transaction =
                Transaction(
                    aggregateId = aggregateId,
                    correlationId = "corr-failed",
                    fromAccountId = AggregateId("acc-from"),
                    toAccountId = AggregateId("acc-to"),
                    amount = BigDecimal("200.00"),
                    moneyWithdrawn = false,
                    moneyDeposited = false,
                    status = TransactionStatus.FAILED,
                    version = 2L,
                    createdAt = now.minusSeconds(1800),
                    updatedAt = now,
                )
            val aggregate = TransactionAggregate(transaction, emptyList())

            val savedRedisTransaction =
                RedisTransaction(
                    id = UUID.fromString(aggregateId.value),
                    correlationId = "corr-failed",
                    fromAccountId = "acc-from",
                    toAccountId = "acc-to",
                    amount = BigDecimal("200.00"),
                    moneyWithdrawn = false,
                    moneyDeposited = false,
                    status = TransactionStatus.FAILED.name,
                    version = 2L,
                    createdAt = now.minusSeconds(1800),
                    updatedAt = now,
                )

            whenever(transactionDataRepository.save(any())).thenReturn(savedRedisTransaction)

            // When
            val result = redisTransactionRepository.upsert(aggregate)

            // Then
            assertThat(result.status).isEqualTo(TransactionStatus.FAILED)
            assertThat(result.version).isEqualTo(2L)
            verify(transactionDataRepository).save(any())
        }
}
