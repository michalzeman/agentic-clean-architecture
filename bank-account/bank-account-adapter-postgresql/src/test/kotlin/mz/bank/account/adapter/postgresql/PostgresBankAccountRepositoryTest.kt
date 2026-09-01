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
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class PostgresBankAccountRepositoryTest {
    private lateinit var bankAccountJdbcRepository: BankAccountJdbcRepository
    private lateinit var transactionRefJdbcRepository: BankAccountTransactionRefJdbcRepository
    private lateinit var postgresBankAccountRepository: PostgresBankAccountRepository

    @BeforeEach
    fun setUp() {
        bankAccountJdbcRepository = mock()
        transactionRefJdbcRepository = mock()
        postgresBankAccountRepository = PostgresBankAccountRepository(bankAccountJdbcRepository, transactionRefJdbcRepository)
    }

    @Test
    fun `should find bankAccount by aggregateId when it exists`(): Unit =
        runBlocking {
            val id = UUID.randomUUID()
            val aggregateId = AggregateId(id.toString())
            val now = Instant.now()
            val accountRow =
                PostgresBankAccount(
                    id = 1L,
                    aggregateId = id,
                    email = "test@example.com",
                    amount = BigDecimal("200.00"),
                    aggregateVersion = 2L,
                    createdAt = now,
                    updatedAt = now,
                )
            val refs =
                listOf(
                    PostgresBankAccountTransactionRef(
                        bankAccountId = 1L,
                        transactionId = "txn-1",
                        state = TransactionRefState.OPENED.name,
                        createdAt = now,
                    ),
                )
            whenever(bankAccountJdbcRepository.findByAggregateId(id)).thenReturn(accountRow)
            whenever(transactionRefJdbcRepository.findAllByBankAccountId(1L)).thenReturn(refs)

            val result = postgresBankAccountRepository.findById(aggregateId)

            assertThat(result).isNotNull
            assertThat(result?.aggregateId).isEqualTo(aggregateId)
            assertThat(result?.email).isEqualTo(Email("test@example.com"))
            assertThat(result?.amount).isEqualByComparingTo(BigDecimal("200.00"))
            assertThat(result?.openedTransactions).containsExactly("txn-1")
            assertThat(result?.version).isEqualTo(2L)
            verify(bankAccountJdbcRepository).findByAggregateId(id)
            verify(transactionRefJdbcRepository).findAllByBankAccountId(1L)
        }

    @Test
    fun `should return null when bankAccount does not exist`(): Unit =
        runBlocking {
            val id = UUID.randomUUID()
            val aggregateId = AggregateId(id.toString())
            whenever(bankAccountJdbcRepository.findByAggregateId(id)).thenReturn(null)

            val result = postgresBankAccountRepository.findById(aggregateId)

            assertThat(result).isNull()
            verify(bankAccountJdbcRepository).findByAggregateId(id)
        }

    @Test
    fun `should insert bankAccount when it does not exist yet`(): Unit =
        runBlocking {
            val aggregateId = AggregateId(UUID.randomUUID().toString())
            val uuid = UUID.fromString(aggregateId.value)
            val now = Instant.now()
            val bankAccount =
                BankAccount(
                    aggregateId = aggregateId,
                    email = Email("new@example.com"),
                    amount = BigDecimal("500.00"),
                    openedTransactions = setOf("txn-open-1"),
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
                    aggregateVersion = 0L,
                    createdAt = now,
                    updatedAt = now,
                )
            whenever(bankAccountJdbcRepository.findByAggregateId(uuid)).thenReturn(null)
            whenever(bankAccountJdbcRepository.save(any())).thenReturn(savedEntity)
            whenever(transactionRefJdbcRepository.saveAll(any<List<PostgresBankAccountTransactionRef>>())).thenAnswer { it.arguments[0] }

            val result = postgresBankAccountRepository.upsert(aggregate)

            assertThat(result.aggregateId).isEqualTo(aggregateId)
            assertThat(result.email).isEqualTo(Email("new@example.com"))
            assertThat(result.amount).isEqualByComparingTo(BigDecimal("500.00"))
            assertThat(result.openedTransactions).containsExactly("txn-open-1")
            verify(bankAccountJdbcRepository).findByAggregateId(uuid)
            verify(bankAccountJdbcRepository).save(any())
            verify(transactionRefJdbcRepository).deleteAllByBankAccountId(1L)
            verify(transactionRefJdbcRepository).saveAll(any<List<PostgresBankAccountTransactionRef>>())
        }

    @Test
    fun `should update bankAccount when it already exists`(): Unit =
        runBlocking {
            val aggregateId = AggregateId(UUID.randomUUID().toString())
            val uuid = UUID.fromString(aggregateId.value)
            val now = Instant.now()
            val bankAccount =
                BankAccount(
                    aggregateId = aggregateId,
                    email = Email("existing@example.com"),
                    amount = BigDecimal("750.00"),
                    finishedTransactions = setOf("txn-done-1"),
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
                    aggregateVersion = 2L,
                    createdAt = now.minusSeconds(3600),
                    updatedAt = now.minusSeconds(100),
                )
            val savedEntity =
                PostgresBankAccount(
                    id = 5L,
                    aggregateId = uuid,
                    email = "existing@example.com",
                    amount = BigDecimal("750.00"),
                    aggregateVersion = 3L,
                    createdAt = now.minusSeconds(3600),
                    updatedAt = now,
                )
            whenever(bankAccountJdbcRepository.findByAggregateId(uuid)).thenReturn(existingEntity)
            whenever(bankAccountJdbcRepository.save(any())).thenReturn(savedEntity)
            whenever(transactionRefJdbcRepository.saveAll(any<List<PostgresBankAccountTransactionRef>>())).thenAnswer { it.arguments[0] }

            val result = postgresBankAccountRepository.upsert(aggregate)

            assertThat(result.aggregateId).isEqualTo(aggregateId)
            assertThat(result.amount).isEqualByComparingTo(BigDecimal("750.00"))
            assertThat(result.version).isEqualTo(3L)
            assertThat(result.finishedTransactions).containsExactly("txn-done-1")
            val captor = argumentCaptor<PostgresBankAccount>()
            verify(bankAccountJdbcRepository).save(captor.capture())
            assertThat(captor.firstValue.version).isEqualTo(existingEntity.version)
            assertThat(captor.firstValue.aggregateVersion).isEqualTo(3L)
            verify(transactionRefJdbcRepository).deleteAllByBankAccountId(5L)
            verify(transactionRefJdbcRepository).saveAll(any<List<PostgresBankAccountTransactionRef>>())
        }

    @Test
    fun `should pass aggregateVersion as-is and carry existingVersion on update`(): Unit =
        runBlocking {
            val aggregateId = AggregateId(UUID.randomUUID().toString())
            val uuid = UUID.fromString(aggregateId.value)
            val now = Instant.now()
            val bankAccount =
                BankAccount(
                    aggregateId = aggregateId,
                    email = Email("lock@example.com"),
                    amount = BigDecimal("750.00"),
                    finishedTransactions = setOf("txn-done-1"),
                    version = 3L,
                    createdAt = now.minusSeconds(3600),
                    updatedAt = now,
                )
            val aggregate = BankAccountAggregate(bankAccount, emptyList())
            val existingEntity =
                PostgresBankAccount(
                    id = 5L,
                    aggregateId = uuid,
                    email = "lock@example.com",
                    amount = BigDecimal("500.00"),
                    aggregateVersion = 2L,
                    version = 4L,
                    createdAt = now.minusSeconds(3600),
                    updatedAt = now.minusSeconds(100),
                )
            val savedEntity =
                PostgresBankAccount(
                    id = 5L,
                    aggregateId = uuid,
                    email = "lock@example.com",
                    amount = BigDecimal("750.00"),
                    aggregateVersion = 3L,
                    version = 4L,
                    createdAt = now.minusSeconds(3600),
                    updatedAt = now,
                )
            whenever(bankAccountJdbcRepository.findByAggregateId(uuid)).thenReturn(existingEntity)
            whenever(bankAccountJdbcRepository.save(any())).thenReturn(savedEntity)
            whenever(transactionRefJdbcRepository.saveAll(any<List<PostgresBankAccountTransactionRef>>())).thenAnswer { it.arguments[0] }

            val result = postgresBankAccountRepository.upsert(aggregate)

            assertThat(result.version).isEqualTo(3L)
            val captor = argumentCaptor<PostgresBankAccount>()
            verify(bankAccountJdbcRepository).save(captor.capture())
            assertThat(captor.firstValue.version).isEqualTo(4L) // existing token carried
            assertThat(captor.firstValue.aggregateVersion).isEqualTo(3L) // domain version as-is
        }

    @Test
    fun `should skip refs save when both transaction sets are empty`(): Unit =
        runBlocking {
            val aggregateId = AggregateId(UUID.randomUUID().toString())
            val uuid = UUID.fromString(aggregateId.value)
            val now = Instant.now()
            val bankAccount =
                BankAccount(
                    aggregateId = aggregateId,
                    email = Email("empty@example.com"),
                    amount = BigDecimal("10.00"),
                    version = 0L,
                    createdAt = now,
                    updatedAt = now,
                )
            val aggregate = BankAccountAggregate(bankAccount, emptyList())
            val savedEntity =
                PostgresBankAccount(
                    id = 9L,
                    aggregateId = uuid,
                    email = "empty@example.com",
                    amount = BigDecimal("10.00"),
                    aggregateVersion = 0L,
                    createdAt = now,
                    updatedAt = now,
                )

            whenever(bankAccountJdbcRepository.findByAggregateId(uuid)).thenReturn(null)
            whenever(bankAccountJdbcRepository.save(any())).thenReturn(savedEntity)

            postgresBankAccountRepository.upsert(aggregate)

            verify(transactionRefJdbcRepository).deleteAllByBankAccountId(9L)
            verify(transactionRefJdbcRepository, never()).saveAll(any<List<PostgresBankAccountTransactionRef>>())
        }

    @Test
    fun `should return true when email exists`(): Unit =
        runBlocking {
            val email = Email("existing@example.com")
            whenever(bankAccountJdbcRepository.existsByEmail(email.value)).thenReturn(true)

            val result = postgresBankAccountRepository.existsByEmail(email)

            assertThat(result).isTrue()
            verify(bankAccountJdbcRepository).existsByEmail(email.value)
        }

    @Test
    fun `should return false when email does not exist`(): Unit =
        runBlocking {
            val email = Email("nonexistent@example.com")
            whenever(bankAccountJdbcRepository.existsByEmail(email.value)).thenReturn(false)

            val result = postgresBankAccountRepository.existsByEmail(email)

            assertThat(result).isFalse()
            verify(bankAccountJdbcRepository).existsByEmail(email.value)
        }
}
