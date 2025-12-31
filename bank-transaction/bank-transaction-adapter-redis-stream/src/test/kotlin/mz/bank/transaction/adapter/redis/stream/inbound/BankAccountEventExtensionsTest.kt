package mz.bank.transaction.adapter.redis.stream.inbound

import mz.bank.account.contract.proto.AccountCreated
import mz.bank.account.contract.proto.BankAccountEvent
import mz.bank.account.contract.proto.TransactionFinished
import mz.bank.account.contract.proto.TransferDepositRolledBack
import mz.bank.account.contract.proto.TransferDepositStarted
import mz.bank.account.contract.proto.TransferWithdrawalRolledBack
import mz.bank.account.contract.proto.TransferWithdrawalStarted
import mz.bank.transaction.application.account.AccountEvent
import mz.shared.domain.AggregateId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class BankAccountEventExtensionsTest {
    @Test
    fun `should map AccountCreated event`() {
        val protoEvent =
            BankAccountEvent
                .newBuilder()
                .setAccountCreated(
                    AccountCreated
                        .newBuilder()
                        .setAggregateId("acc-123")
                        .setUpdatedAtEpochMillis(1000L)
                        .setEmail("test@example.com")
                        .setInitialBalance("1000.00")
                        .build(),
                ).build()

        val result = protoEvent.toAccountEvent()

        assertThat(result).isInstanceOf(AccountEvent.AccountCreatedEvent::class.java)
        val event = result as AccountEvent.AccountCreatedEvent
        assertThat(event.accountId).isEqualTo(AggregateId("acc-123"))
    }

    @Test
    fun `should map TransferWithdrawalStarted event`() {
        val protoEvent =
            BankAccountEvent
                .newBuilder()
                .setTransferWithdrawalStarted(
                    TransferWithdrawalStarted
                        .newBuilder()
                        .setAggregateId("acc-123")
                        .setUpdatedAtEpochMillis(1000L)
                        .setTransactionId("tx-456")
                        .setAmount("100.50")
                        .build(),
                ).build()

        val result = protoEvent.toAccountEvent()

        assertThat(result).isInstanceOf(AccountEvent.TransferWithdrawalStartedEvent::class.java)
        val event = result as AccountEvent.TransferWithdrawalStartedEvent
        assertThat(event.accountId).isEqualTo(AggregateId("acc-123"))
        assertThat(event.transactionId).isEqualTo("tx-456")
        assertThat(event.amount).isEqualTo(BigDecimal("100.50"))
    }

    @Test
    fun `should map TransferDepositStarted event`() {
        val protoEvent =
            BankAccountEvent
                .newBuilder()
                .setTransferDepositStarted(
                    TransferDepositStarted
                        .newBuilder()
                        .setAggregateId("acc-789")
                        .setUpdatedAtEpochMillis(2000L)
                        .setTransactionId("tx-456")
                        .setAmount("200.75")
                        .build(),
                ).build()

        val result = protoEvent.toAccountEvent()

        assertThat(result).isInstanceOf(AccountEvent.TransferDepositStartedEvent::class.java)
        val event = result as AccountEvent.TransferDepositStartedEvent
        assertThat(event.accountId).isEqualTo(AggregateId("acc-789"))
        assertThat(event.transactionId).isEqualTo("tx-456")
        assertThat(event.amount).isEqualTo(BigDecimal("200.75"))
    }

    @Test
    fun `should map TransactionFinished event`() {
        val protoEvent =
            BankAccountEvent
                .newBuilder()
                .setTransactionFinished(
                    TransactionFinished
                        .newBuilder()
                        .setAggregateId("acc-999")
                        .setUpdatedAtEpochMillis(3000L)
                        .setTransactionId("tx-456")
                        .build(),
                ).build()

        val result = protoEvent.toAccountEvent()

        assertThat(result).isInstanceOf(AccountEvent.TransactionFinishedEvent::class.java)
        val event = result as AccountEvent.TransactionFinishedEvent
        assertThat(event.accountId).isEqualTo(AggregateId("acc-999"))
        assertThat(event.transactionId).isEqualTo("tx-456")
    }

    @Test
    fun `should map TransferWithdrawalRolledBack event`() {
        val protoEvent =
            BankAccountEvent
                .newBuilder()
                .setTransferWithdrawalRolledBack(
                    TransferWithdrawalRolledBack
                        .newBuilder()
                        .setAggregateId("acc-111")
                        .setUpdatedAtEpochMillis(4000L)
                        .setTransactionId("tx-888")
                        .setAmount("50.25")
                        .build(),
                ).build()

        val result = protoEvent.toAccountEvent()

        assertThat(result).isInstanceOf(AccountEvent.TransferWithdrawalRolledBackEvent::class.java)
        val event = result as AccountEvent.TransferWithdrawalRolledBackEvent
        assertThat(event.accountId).isEqualTo(AggregateId("acc-111"))
        assertThat(event.transactionId).isEqualTo("tx-888")
        assertThat(event.amount).isEqualTo(BigDecimal("50.25"))
    }

    @Test
    fun `should map TransferDepositRolledBack event`() {
        val protoEvent =
            BankAccountEvent
                .newBuilder()
                .setTransferDepositRolledBack(
                    TransferDepositRolledBack
                        .newBuilder()
                        .setAggregateId("acc-222")
                        .setUpdatedAtEpochMillis(5000L)
                        .setTransactionId("tx-888")
                        .setAmount("75.00")
                        .build(),
                ).build()

        val result = protoEvent.toAccountEvent()

        assertThat(result).isInstanceOf(AccountEvent.TransferDepositRolledBackEvent::class.java)
        val event = result as AccountEvent.TransferDepositRolledBackEvent
        assertThat(event.accountId).isEqualTo(AggregateId("acc-222"))
        assertThat(event.transactionId).isEqualTo("tx-888")
        assertThat(event.amount).isEqualTo(BigDecimal("75.00"))
    }

    @Test
    fun `should return DefaultAccountEvent for empty event`() {
        val protoEvent = BankAccountEvent.newBuilder().build()

        val result = protoEvent.toAccountEvent()

        assertThat(result).isEqualTo(AccountEvent.DefaultAccountEvent)
    }
}
