package mz.bank.transaction.adapter.postgresql.account

import mz.bank.transaction.domain.account.AccountView
import mz.shared.domain.AggregateId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class PostgresAccountViewMapperTest {
    // ==================== toAccountView() Mapping Tests ====================

    @Test
    fun `should convert PostgresAccountView to AccountView correctly`() {
        // Given
        val id = UUID.randomUUID()
        val postgresAccountView =
            PostgresAccountView(
                id = 1L,
                accountId = id,
            )

        // When
        val accountView = postgresAccountView.toAccountView()

        // Then
        assertThat(accountView.accountId).isEqualTo(AggregateId(id.toString()))
    }

    // ==================== toPostgresAccountView() Mapping Tests ====================

    @Test
    fun `should convert AccountView to PostgresAccountView with null id for new entity`() {
        // Given
        val accountId = AggregateId(UUID.randomUUID().toString())
        val accountView = AccountView(accountId = accountId)

        // When
        val postgresAccountView = accountView.toPostgresAccountView()

        // Then
        assertThat(postgresAccountView.id).isNull()
        assertThat(postgresAccountView.accountId).isEqualTo(UUID.fromString(accountId.value))
    }

    @Test
    fun `should carry existingId forward when converting to PostgresAccountView`() {
        // Given
        val accountId = AggregateId(UUID.randomUUID().toString())
        val accountView = AccountView(accountId = accountId)

        // When
        val postgresAccountView = accountView.toPostgresAccountView(existingId = 7L)

        // Then
        assertThat(postgresAccountView.id).isEqualTo(7L)
        assertThat(postgresAccountView.accountId).isEqualTo(UUID.fromString(accountId.value))
    }

    // ==================== Round-trip Mapping Tests ====================

    @Test
    fun `should preserve data through round-trip conversion`() {
        // Given
        val accountId = AggregateId(UUID.randomUUID().toString())
        val originalAccountView = AccountView(accountId = accountId)

        // When - convert to Postgres entity and back
        val postgresAccountView = originalAccountView.toPostgresAccountView()
        val convertedBack = postgresAccountView.toAccountView()

        // Then
        assertThat(convertedBack.accountId).isEqualTo(originalAccountView.accountId)
    }
}
