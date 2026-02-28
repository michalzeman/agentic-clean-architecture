package mz.bank.transaction.adapter.postgresql.account

import mz.bank.transaction.domain.account.AccountView
import mz.shared.domain.AggregateId
import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.util.UUID

/**
 * Spring Data JDBC persistence entity for AccountView read model.
 * Uses a BIGSERIAL surrogate PK and account_id UUID for domain identity.
 */
@Table("account_view")
internal data class PostgresAccountView(
    @field:Id val id: Long? = null,
    @field:Column("account_id") val accountId: UUID,
)

/**
 * Converts PostgresAccountView persistence entity to AccountView domain entity.
 */
internal fun PostgresAccountView.toAccountView(): AccountView =
    AccountView(
        accountId = AggregateId(accountId.toString()),
    )

/**
 * Converts AccountView domain entity to PostgresAccountView persistence entity.
 * Carries the existing surrogate id forward for updates (null triggers INSERT).
 */
internal fun AccountView.toPostgresAccountView(existingId: Long? = null): PostgresAccountView =
    PostgresAccountView(
        id = existingId,
        accountId = UUID.fromString(accountId.value),
    )
