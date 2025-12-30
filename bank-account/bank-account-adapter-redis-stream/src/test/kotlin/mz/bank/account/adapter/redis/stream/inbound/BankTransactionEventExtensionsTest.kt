package mz.bank.account.adapter.redis.stream.inbound

import mz.bank.account.application.transaction.InboundBankTransactionEvent
import mz.bank.transaction.contract.proto.BankTransactionEvent
import mz.bank.transaction.contract.proto.TransactionCreated
import mz.bank.transaction.contract.proto.TransactionDepositRolledBack
import mz.bank.transaction.contract.proto.TransactionFailed
import mz.bank.transaction.contract.proto.TransactionFinished
import mz.bank.transaction.contract.proto.TransactionMoneyDeposited
import mz.bank.transaction.contract.proto.TransactionMoneyWithdrawn
import mz.bank.transaction.contract.proto.TransactionRolledBack
import mz.bank.transaction.contract.proto.TransactionWithdrawRolledBack
import mz.shared.domain.AggregateId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

class BankTransactionEventExtensionsTest {
    @Test
    fun `should map TransactionCreated event`() {
        val protoEvent =
            BankTransactionEvent
                .newBuilder()
                .setTransactionCreated(
                    TransactionCreated
                        .newBuilder()
                        .setAggregateId("tx-123")
                        .setCorrelationId("corr-123")
                        .setFromAccountId("acc-1")
                        .setToAccountId("acc-2")
                        .setAmount("100.00")
                        .setUpdatedAtEpochMillis(1000L)
                        .build(),
                ).build()

        val result = protoEvent.toInboundEvent()

        assertThat(result).isInstanceOf(InboundBankTransactionEvent.TransactionCreated::class.java)
        val event = result as InboundBankTransactionEvent.TransactionCreated
        assertThat(event.aggregateId).isEqualTo(AggregateId("tx-123"))
        assertThat(event.correlationId).isEqualTo("corr-123")
        assertThat(event.fromAccountId).isEqualTo(AggregateId("acc-1"))
        assertThat(event.toAccountId).isEqualTo(AggregateId("acc-2"))
        assertThat(event.amount).isEqualTo(BigDecimal("100.00"))
        assertThat(event.updatedAt).isEqualTo(Instant.ofEpochMilli(1000L))
    }

    @Test
    fun `should map TransactionMoneyWithdrawn event`() {
        val protoEvent =
            BankTransactionEvent
                .newBuilder()
                .setTransactionMoneyWithdrawn(
                    TransactionMoneyWithdrawn
                        .newBuilder()
                        .setAggregateId("tx-123")
                        .setCorrelationId("corr-123")
                        .setAccountId("acc-1")
                        .setUpdatedAtEpochMillis(1000L)
                        .build(),
                ).build()

        val result = protoEvent.toInboundEvent()

        assertThat(result).isInstanceOf(InboundBankTransactionEvent.TransactionMoneyWithdrawn::class.java)
        val event = result as InboundBankTransactionEvent.TransactionMoneyWithdrawn
        assertThat(event.aggregateId).isEqualTo(AggregateId("tx-123"))
        assertThat(event.correlationId).isEqualTo("corr-123")
        assertThat(event.accountId).isEqualTo(AggregateId("acc-1"))
        assertThat(event.updatedAt).isEqualTo(Instant.ofEpochMilli(1000L))
    }

    @Test
    fun `should map TransactionMoneyDeposited event`() {
        val protoEvent =
            BankTransactionEvent
                .newBuilder()
                .setTransactionMoneyDeposited(
                    TransactionMoneyDeposited
                        .newBuilder()
                        .setAggregateId("tx-123")
                        .setCorrelationId("corr-123")
                        .setAccountId("acc-2")
                        .setUpdatedAtEpochMillis(1000L)
                        .build(),
                ).build()

        val result = protoEvent.toInboundEvent()

        assertThat(result).isInstanceOf(InboundBankTransactionEvent.TransactionMoneyDeposited::class.java)
        val event = result as InboundBankTransactionEvent.TransactionMoneyDeposited
        assertThat(event.aggregateId).isEqualTo(AggregateId("tx-123"))
        assertThat(event.correlationId).isEqualTo("corr-123")
        assertThat(event.accountId).isEqualTo(AggregateId("acc-2"))
        assertThat(event.updatedAt).isEqualTo(Instant.ofEpochMilli(1000L))
    }

    @Test
    fun `should map TransactionFinished event`() {
        val protoEvent =
            BankTransactionEvent
                .newBuilder()
                .setTransactionFinished(
                    TransactionFinished
                        .newBuilder()
                        .setAggregateId("tx-123")
                        .setCorrelationId("corr-123")
                        .setFromAccountId("acc-1")
                        .setToAccountId("acc-2")
                        .setUpdatedAtEpochMillis(1000L)
                        .build(),
                ).build()

        val result = protoEvent.toInboundEvent()

        assertThat(result).isInstanceOf(InboundBankTransactionEvent.TransactionFinished::class.java)
        val event = result as InboundBankTransactionEvent.TransactionFinished
        assertThat(event.aggregateId).isEqualTo(AggregateId("tx-123"))
        assertThat(event.correlationId).isEqualTo("corr-123")
        assertThat(event.fromAccountId).isEqualTo(AggregateId("acc-1"))
        assertThat(event.toAccountId).isEqualTo(AggregateId("acc-2"))
        assertThat(event.updatedAt).isEqualTo(Instant.ofEpochMilli(1000L))
    }

    @Test
    fun `should map TransactionFailed event`() {
        val protoEvent =
            BankTransactionEvent
                .newBuilder()
                .setTransactionFailed(
                    TransactionFailed
                        .newBuilder()
                        .setAggregateId("tx-123")
                        .setCorrelationId("corr-123")
                        .setFromAccountId("acc-1")
                        .setToAccountId("acc-2")
                        .setAmount("100.00")
                        .setReason("Insufficient funds")
                        .setUpdatedAtEpochMillis(1000L)
                        .build(),
                ).build()

        val result = protoEvent.toInboundEvent()

        assertThat(result).isInstanceOf(InboundBankTransactionEvent.TransactionFailed::class.java)
        val event = result as InboundBankTransactionEvent.TransactionFailed
        assertThat(event.aggregateId).isEqualTo(AggregateId("tx-123"))
        assertThat(event.correlationId).isEqualTo("corr-123")
        assertThat(event.fromAccountId).isEqualTo(AggregateId("acc-1"))
        assertThat(event.toAccountId).isEqualTo(AggregateId("acc-2"))
        assertThat(event.amount).isEqualTo(BigDecimal("100.00"))
        assertThat(event.reason).isEqualTo("Insufficient funds")
        assertThat(event.updatedAt).isEqualTo(Instant.ofEpochMilli(1000L))
    }

    @Test
    fun `should map TransactionRolledBack event`() {
        val protoEvent =
            BankTransactionEvent
                .newBuilder()
                .setTransactionRolledBack(
                    TransactionRolledBack
                        .newBuilder()
                        .setAggregateId("tx-123")
                        .setCorrelationId("corr-123")
                        .setFromAccountId("acc-1")
                        .setToAccountId("acc-2")
                        .setAmount("100.00")
                        .setUpdatedAtEpochMillis(1000L)
                        .build(),
                ).build()

        val result = protoEvent.toInboundEvent()

        assertThat(result).isInstanceOf(InboundBankTransactionEvent.TransactionRolledBack::class.java)
        val event = result as InboundBankTransactionEvent.TransactionRolledBack
        assertThat(event.aggregateId).isEqualTo(AggregateId("tx-123"))
        assertThat(event.correlationId).isEqualTo("corr-123")
        assertThat(event.fromAccountId).isEqualTo(AggregateId("acc-1"))
        assertThat(event.toAccountId).isEqualTo(AggregateId("acc-2"))
        assertThat(event.amount).isEqualTo(BigDecimal("100.00"))
        assertThat(event.updatedAt).isEqualTo(Instant.ofEpochMilli(1000L))
    }

    @Test
    fun `should map TransactionWithdrawRolledBack event`() {
        val protoEvent =
            BankTransactionEvent
                .newBuilder()
                .setTransactionWithdrawRolledBack(
                    TransactionWithdrawRolledBack
                        .newBuilder()
                        .setAggregateId("tx-123")
                        .setCorrelationId("corr-123")
                        .setFromAccountId("acc-1")
                        .setToAccountId("acc-2")
                        .setAmount("100.00")
                        .setUpdatedAtEpochMillis(1000L)
                        .build(),
                ).build()

        val result = protoEvent.toInboundEvent()

        assertThat(result).isInstanceOf(InboundBankTransactionEvent.TransactionWithdrawRolledBack::class.java)
        val event = result as InboundBankTransactionEvent.TransactionWithdrawRolledBack
        assertThat(event.aggregateId).isEqualTo(AggregateId("tx-123"))
        assertThat(event.correlationId).isEqualTo("corr-123")
        assertThat(event.fromAccountId).isEqualTo(AggregateId("acc-1"))
        assertThat(event.amount).isEqualTo(BigDecimal("100.00"))
        assertThat(event.updatedAt).isEqualTo(Instant.ofEpochMilli(1000L))
    }

    @Test
    fun `should map TransactionDepositRolledBack event`() {
        val protoEvent =
            BankTransactionEvent
                .newBuilder()
                .setTransactionDepositRolledBack(
                    TransactionDepositRolledBack
                        .newBuilder()
                        .setAggregateId("tx-123")
                        .setCorrelationId("corr-123")
                        .setFromAccountId("acc-1")
                        .setToAccountId("acc-2")
                        .setAmount("100.00")
                        .setUpdatedAtEpochMillis(1000L)
                        .build(),
                ).build()

        val result = protoEvent.toInboundEvent()

        assertThat(result).isInstanceOf(InboundBankTransactionEvent.TransactionDepositRolledBack::class.java)
        val event = result as InboundBankTransactionEvent.TransactionDepositRolledBack
        assertThat(event.aggregateId).isEqualTo(AggregateId("tx-123"))
        assertThat(event.correlationId).isEqualTo("corr-123")
        assertThat(event.toAccountId).isEqualTo(AggregateId("acc-2"))
        assertThat(event.amount).isEqualTo(BigDecimal("100.00"))
        assertThat(event.updatedAt).isEqualTo(Instant.ofEpochMilli(1000L))
    }

    @Test
    fun `should return Default event for empty event`() {
        val protoEvent = BankTransactionEvent.newBuilder().build()

        val result = protoEvent.toInboundEvent()

        assertThat(result).isInstanceOf(InboundBankTransactionEvent.Default::class.java)
        val event = result as InboundBankTransactionEvent.Default
        assertThat(event.aggregateId).isEqualTo(AggregateId("unknown"))
        assertThat(event.correlationId).isEqualTo("unknown")
    }
}
