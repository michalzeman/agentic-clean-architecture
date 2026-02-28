package mz.bank.account.adapter.postgresql

import kotlinx.coroutines.runBlocking
import mz.bank.account.domain.BankAccount
import mz.bank.account.domain.BankAccountAggregate
import mz.bank.account.domain.Email
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

class PostgresBankAccountRepositoryTest {
    private lateinit var bankAccountJdbcRepository: BankAccountJdbcRepository
    private lateinit var postgresBankAccountRepository: PostgresBankAccountRepository

    @BeforeEach
    fun setUp() {
        bankAccountJdbcRepository = mock()
        postgresBankAccountRepository = PostgresBankAccountRepository(bankAccountJdbcRepository)
    }

    // ==================== findById Tests ====================

    @Test
    fun `should find bankAccount by aggregateId when it exists`(): Unit =
        runBlocking {
            // Given
            val id = UUID.randomUUID()
            val aggregateId = AggregateId(id.toString())
            val now = Instant.now()
            val postgresBankAccount =
                PostgresBankAccount(
                    id = 1L,
                    aggregateId = id,
                    email = "test@example.com",
                    amount = BigDecimal("200.00"),
                    openedTransactions = jsonbOf("""["txn-1"]"""),
                    finishedTransactions = jsonbOf("[]"),
                    version = 2L,
                    createdAt = now,
                    updatedAt = now,
                )
            whenever(bankAccountJdbcRepository.findByAggregateId(id)).thenReturn(postgresBankAccount)

            // When
            val result = postgresBankAccountRepository.findById(aggregateId)

            // Then
            assertThat(result).isNotNull
            assertThat(result?.aggregateId).isEqualTo(aggregateId)
            assertThat(result?.email).isEqualTo(Email("test@example.com"))
            assertThat(result?.amount).isEqualByComparingTo(BigDecimal("200.00"))
            assertThat(result?.openedTransactions).containsExactly("txn-1")
            assertThat(result?.version).isEqualTo(2L)
            verify(bankAccountJdbcRepository).findByAggregateId(id)
        }

    @Test
    fun `should return null when bankAccount does not exist`(): Unit =
        runBlocking {
            // Given
            val id = UUID.randomUUID()
            val aggregateId = AggregateId(id.toString())
            whenever(bankAccountJdbcRepository.findByAggregateId(id)).thenReturn(null)

            // When
            val result = postgresBankAccountRepository.findById(aggregateId)

            // Then
            assertThat(result).isNull()
            verify(bankAccountJdbcRepository).findByAggregateId(id)
        }

    // ==================== upsert Tests ====================

    @Test
    fun `should insert bankAccount when it does not exist yet`(): Unit =
        runBlocking {
            // Given
            val aggregateId = AggregateId(UUID.randomUUID().toString())
            val uuid = UUID.fromString(aggregateId.value)
            val now = Instant.now()
            val bankAccount =
                BankAccount(
                    aggregateId = aggregateId,
                    email = Email("new@example.com"),
                    amount = BigDecimal("500.00"),
                    version = 0L,
                    createdAt = now,
                    updatedAt = now,
                )
            val aggregate = BankAccountAggregate(bankAccount, emptyList())
            val savedEntity =
                PostgresBankAccount(
                    id = 1L,
                    aggregateId = uuid,
                    email = "new@example.com",
                    amount = BigDecimal("500.00"),
                    openedTransactions = jsonbOf("[]"),
                    finishedTransactions = jsonbOf("[]"),
                    version = 0L,
                    createdAt = now,
                    updatedAt = now,
                )
            whenever(bankAccountJdbcRepository.findByAggregateId(uuid)).thenReturn(null)
            whenever(bankAccountJdbcRepository.save(any())).thenReturn(savedEntity)

            // When
            val result = postgresBankAccountRepository.upsert(aggregate)

            // Then
            assertThat(result.aggregateId).isEqualTo(aggregateId)
            assertThat(result.email).isEqualTo(Email("new@example.com"))
            assertThat(result.amount).isEqualByComparingTo(BigDecimal("500.00"))
            verify(bankAccountJdbcRepository).findByAggregateId(uuid)
            verify(bankAccountJdbcRepository).save(any())
        }

    @Test
    fun `should update bankAccount when it already exists`(): Unit =
        runBlocking {
            // Given
            val aggregateId = AggregateId(UUID.randomUUID().toString())
            val uuid = UUID.fromString(aggregateId.value)
            val now = Instant.now()
            val bankAccount =
                BankAccount(
                    aggregateId = aggregateId,
                    email = Email("existing@example.com"),
                    amount = BigDecimal("750.00"),
                    version = 3L,
                    createdAt = now.minusSeconds(3600),
                    updatedAt = now,
                )
            val aggregate = BankAccountAggregate(bankAccount, emptyList())
            val existingEntity =
                PostgresBankAccount(
                    id = 5L,
                    aggregateId = uuid,
                    email = "existing@example.com",
                    amount = BigDecimal("500.00"),
                    openedTransactions = jsonbOf("[]"),
                    finishedTransactions = jsonbOf("[]"),
                    version = 2L,
                    createdAt = now.minusSeconds(3600),
                    updatedAt = now.minusSeconds(100),
                )
            val savedEntity =
                PostgresBankAccount(
                    id = 5L,
                    aggregateId = uuid,
                    email = "existing@example.com",
                    amount = BigDecimal("750.00"),
                    openedTransactions = jsonbOf("[]"),
                    finishedTransactions = jsonbOf("[]"),
                    version = 3L,
                    createdAt = now.minusSeconds(3600),
                    updatedAt = now,
                )
            whenever(bankAccountJdbcRepository.findByAggregateId(uuid)).thenReturn(existingEntity)
            whenever(bankAccountJdbcRepository.save(any())).thenReturn(savedEntity)

            // When
            val result = postgresBankAccountRepository.upsert(aggregate)

            // Then
            assertThat(result.aggregateId).isEqualTo(aggregateId)
            assertThat(result.amount).isEqualByComparingTo(BigDecimal("750.00"))
            assertThat(result.version).isEqualTo(3L)
            verify(bankAccountJdbcRepository).save(any())
        }

    // ==================== existsByEmail Tests ====================

    @Test
    fun `should return true when email exists`(): Unit =
        runBlocking {
            // Given
            val email = Email("existing@example.com")
            whenever(bankAccountJdbcRepository.existsByEmail(email.value)).thenReturn(true)

            // When
            val result = postgresBankAccountRepository.existsByEmail(email)

            // Then
            assertThat(result).isTrue()
            verify(bankAccountJdbcRepository).existsByEmail(email.value)
        }

    @Test
    fun `should return false when email does not exist`(): Unit =
        runBlocking {
            // Given
            val email = Email("nonexistent@example.com")
            whenever(bankAccountJdbcRepository.existsByEmail(email.value)).thenReturn(false)

            // When
            val result = postgresBankAccountRepository.existsByEmail(email)

            // Then
            assertThat(result).isFalse()
            verify(bankAccountJdbcRepository).existsByEmail(email.value)
        }
}
