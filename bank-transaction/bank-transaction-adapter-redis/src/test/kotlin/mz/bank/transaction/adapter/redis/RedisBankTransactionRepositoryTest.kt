package mz.bank.transaction.adapter.redis

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
import java.util.Optional
import java.util.UUID

class RedisBankTransactionRepositoryTest {
    private lateinit var transactionDataRepository: BankTransactionDataRepository
    private lateinit var redisTransactionRepository: RedisBankTransactionRepository

    @BeforeEach
    fun setUp() {
        transactionDataRepository = mock()
        redisTransactionRepository = RedisBankTransactionRepository(transactionDataRepository)
    }

    // ==================== findById Tests ====================

    @Test
    fun `should find bankTransaction by id when it exists`(): Unit =
        runBlocking {
            // Given
            val id = UUID.randomUUID()
            val aggregateId = AggregateId(id.toString())
            val now = Instant.now()
            val redisBankTransaction =
                RedisBankTransaction(
                    id = id,
                    correlationId = "corr-001",
                    fromAccountId = "acc-001",
                    toAccountId = "acc-002",
                    amount = BigDecimal("500.00"),
                    moneyWithdrawn = true,
                    moneyDeposited = false,
                    status = BankTransactionStatus.CREATED.name,
                    version = 1L,
                    createdAt = now,
                    updatedAt = now,
                )

            whenever(transactionDataRepository.findById(id)).thenReturn(Optional.of(redisBankTransaction))

            // When
            val result = redisTransactionRepository.findById(aggregateId)

            // Then
            assertThat(result).isNotNull
            assertThat(result?.aggregateId).isEqualTo(aggregateId)
            assertThat(result?.correlationId).isEqualTo("corr-001")
            assertThat(result?.amount).isEqualByComparingTo(BigDecimal("500.00"))
            assertThat(result?.moneyWithdrawn).isTrue()
            assertThat(result?.moneyDeposited).isFalse()
            assertThat(result?.status).isEqualTo(BankTransactionStatus.CREATED)
            verify(transactionDataRepository).findById(id)
        }

    @Test
    fun `should return null when bankTransaction does not exist`(): Unit =
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
    fun `should upsert bankTransaction aggregate successfully`(): Unit =
        runBlocking {
            // Given
            val aggregateId = AggregateId(UUID.randomUUID().toString())
            val now = Instant.now()
            val bankTransaction =
                BankTransaction(
                    aggregateId = aggregateId,
                    correlationId = "corr-upsert",
                    fromAccountId = AggregateId("acc-from"),
                    toAccountId = AggregateId("acc-to"),
                    amount = BigDecimal("750.00"),
                    moneyWithdrawn = false,
                    moneyDeposited = false,
                    status = BankTransactionStatus.CREATED,
                    version = 0L,
                    createdAt = now,
                    updatedAt = now,
                )
            val aggregate = BankTransactionAggregate(bankTransaction, emptyList())

            val savedRedisTransaction =
                RedisBankTransaction(
                    id = UUID.fromString(aggregateId.value),
                    correlationId = "corr-upsert",
                    fromAccountId = "acc-from",
                    toAccountId = "acc-to",
                    amount = BigDecimal("750.00"),
                    moneyWithdrawn = false,
                    moneyDeposited = false,
                    status = BankTransactionStatus.CREATED.name,
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
            assertThat(result.status).isEqualTo(BankTransactionStatus.CREATED)
            verify(transactionDataRepository).save(any())
        }

    @Test
    fun `should preserve domain events when upserting`(): Unit =
        runBlocking {
            // Given
            val aggregateId = AggregateId(UUID.randomUUID().toString())
            val now = Instant.now()
            val bankTransaction =
                BankTransaction(
                    aggregateId = aggregateId,
                    correlationId = "corr-events",
                    fromAccountId = AggregateId("acc-from"),
                    toAccountId = AggregateId("acc-to"),
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

            val savedRedisTransaction =
                RedisBankTransaction(
                    id = UUID.fromString(aggregateId.value),
                    correlationId = "corr-events",
                    fromAccountId = "acc-from",
                    toAccountId = "acc-to",
                    amount = BigDecimal("300.00"),
                    moneyWithdrawn = true,
                    moneyDeposited = false,
                    status = BankTransactionStatus.CREATED.name,
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
            val bankTransaction =
                BankTransaction(
                    aggregateId = aggregateId,
                    correlationId = "corr-finished",
                    fromAccountId = AggregateId("acc-from"),
                    toAccountId = AggregateId("acc-to"),
                    amount = BigDecimal("1000.00"),
                    moneyWithdrawn = true,
                    moneyDeposited = true,
                    status = BankTransactionStatus.FINISHED,
                    version = 5L,
                    createdAt = now.minusSeconds(3600),
                    updatedAt = now,
                )
            val aggregate = BankTransactionAggregate(bankTransaction, emptyList())

            val savedRedisTransaction =
                RedisBankTransaction(
                    id = UUID.fromString(aggregateId.value),
                    correlationId = "corr-finished",
                    fromAccountId = "acc-from",
                    toAccountId = "acc-to",
                    amount = BigDecimal("1000.00"),
                    moneyWithdrawn = true,
                    moneyDeposited = true,
                    status = BankTransactionStatus.FINISHED.name,
                    version = 5L,
                    createdAt = now.minusSeconds(3600),
                    updatedAt = now,
                )

            whenever(transactionDataRepository.save(any())).thenReturn(savedRedisTransaction)

            // When
            val result = redisTransactionRepository.upsert(aggregate)

            // Then
            assertThat(result.status).isEqualTo(BankTransactionStatus.FINISHED)
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
            val bankTransaction =
                BankTransaction(
                    aggregateId = aggregateId,
                    correlationId = "corr-failed",
                    fromAccountId = AggregateId("acc-from"),
                    toAccountId = AggregateId("acc-to"),
                    amount = BigDecimal("200.00"),
                    moneyWithdrawn = false,
                    moneyDeposited = false,
                    status = BankTransactionStatus.FAILED,
                    version = 2L,
                    createdAt = now.minusSeconds(1800),
                    updatedAt = now,
                )
            val aggregate = BankTransactionAggregate(bankTransaction, emptyList())

            val savedRedisTransaction =
                RedisBankTransaction(
                    id = UUID.fromString(aggregateId.value),
                    correlationId = "corr-failed",
                    fromAccountId = "acc-from",
                    toAccountId = "acc-to",
                    amount = BigDecimal("200.00"),
                    moneyWithdrawn = false,
                    moneyDeposited = false,
                    status = BankTransactionStatus.FAILED.name,
                    version = 2L,
                    createdAt = now.minusSeconds(1800),
                    updatedAt = now,
                )

            whenever(transactionDataRepository.save(any())).thenReturn(savedRedisTransaction)

            // When
            val result = redisTransactionRepository.upsert(aggregate)

            // Then
            assertThat(result.status).isEqualTo(BankTransactionStatus.FAILED)
            assertThat(result.version).isEqualTo(2L)
            verify(transactionDataRepository).save(any())
        }
}
