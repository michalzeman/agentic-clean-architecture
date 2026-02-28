package mz.bank.transaction.adapter.postgresql.account

import kotlinx.coroutines.runBlocking
import mz.bank.transaction.domain.account.AccountView
import mz.shared.domain.AggregateId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

class PostgresAccountViewRepositoryTest {
    private lateinit var accountViewJdbcRepository: AccountViewJdbcRepository
    private lateinit var postgresAccountViewRepository: PostgresAccountViewRepository

    @BeforeEach
    fun setUp() {
        accountViewJdbcRepository = mock()
        postgresAccountViewRepository = PostgresAccountViewRepository(accountViewJdbcRepository)
    }

    // ==================== findById Tests ====================

    @Test
    fun `should find accountView by accountId when it exists`(): Unit =
        runBlocking {
            // Given
            val id = UUID.randomUUID()
            val accountId = AggregateId(id.toString())
            val postgresAccountView =
                PostgresAccountView(
                    id = 1L,
                    accountId = id,
                )
            whenever(accountViewJdbcRepository.findByAccountId(id)).thenReturn(postgresAccountView)

            // When
            val result = postgresAccountViewRepository.findById(accountId)

            // Then
            assertThat(result).isNotNull
            assertThat(result?.accountId).isEqualTo(accountId)
            verify(accountViewJdbcRepository).findByAccountId(id)
        }

    @Test
    fun `should return null when accountView does not exist`(): Unit =
        runBlocking {
            // Given
            val id = UUID.randomUUID()
            val accountId = AggregateId(id.toString())
            whenever(accountViewJdbcRepository.findByAccountId(id)).thenReturn(null)

            // When
            val result = postgresAccountViewRepository.findById(accountId)

            // Then
            assertThat(result).isNull()
            verify(accountViewJdbcRepository).findByAccountId(id)
        }

    // ==================== upsert Tests ====================

    @Test
    fun `should insert accountView when it does not exist yet`(): Unit =
        runBlocking {
            // Given
            val accountId = AggregateId(UUID.randomUUID().toString())
            val uuid = UUID.fromString(accountId.value)
            val accountView = AccountView(accountId = accountId)
            val savedEntity =
                PostgresAccountView(
                    id = 1L,
                    accountId = uuid,
                )
            whenever(accountViewJdbcRepository.findByAccountId(uuid)).thenReturn(null)
            whenever(accountViewJdbcRepository.save(any())).thenReturn(savedEntity)

            // When
            val result = postgresAccountViewRepository.upsert(accountView)

            // Then
            assertThat(result.accountId).isEqualTo(accountId)
            verify(accountViewJdbcRepository).findByAccountId(uuid)
            verify(accountViewJdbcRepository).save(any())
        }

    @Test
    fun `should update accountView when it already exists`(): Unit =
        runBlocking {
            // Given
            val accountId = AggregateId(UUID.randomUUID().toString())
            val uuid = UUID.fromString(accountId.value)
            val accountView = AccountView(accountId = accountId)
            val existingEntity =
                PostgresAccountView(
                    id = 4L,
                    accountId = uuid,
                )
            val savedEntity =
                PostgresAccountView(
                    id = 4L,
                    accountId = uuid,
                )
            whenever(accountViewJdbcRepository.findByAccountId(uuid)).thenReturn(existingEntity)
            whenever(accountViewJdbcRepository.save(any())).thenReturn(savedEntity)

            // When
            val result = postgresAccountViewRepository.upsert(accountView)

            // Then
            assertThat(result.accountId).isEqualTo(accountId)
            verify(accountViewJdbcRepository).findByAccountId(uuid)
            verify(accountViewJdbcRepository).save(any())
        }
}
