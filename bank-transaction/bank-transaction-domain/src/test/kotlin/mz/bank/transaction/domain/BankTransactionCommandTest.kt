package mz.bank.transaction.domain

import mz.shared.domain.AggregateId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class BankTransactionCommandTest {
    @Test
    fun `should create valid CreateBankTransaction command`() {
        // Given & When
        val command =
            BankTransactionCommand.CreateBankTransaction(
                correlationId = "corr-001",
                fromAccountId = AggregateId("acc-from"),
                toAccountId = AggregateId("acc-to"),
                amount = BigDecimal("100.00"),
            )

        // Then
        assertThat(command.correlationId).isEqualTo("corr-001")
        assertThat(command.fromAccountId).isEqualTo(AggregateId("acc-from"))
        assertThat(command.toAccountId).isEqualTo(AggregateId("acc-to"))
        assertThat(command.amount).isEqualByComparingTo(BigDecimal("100.00"))
    }

    @Test
    fun `should fail to create command with same source and destination account`() {
        // When & Then
        assertThatThrownBy {
            BankTransactionCommand.CreateBankTransaction(
                correlationId = "corr-001",
                fromAccountId = AggregateId("acc-same"),
                toAccountId = AggregateId("acc-same"),
                amount = BigDecimal("100.00"),
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Cannot create transaction with same source and destination account")
    }
}
