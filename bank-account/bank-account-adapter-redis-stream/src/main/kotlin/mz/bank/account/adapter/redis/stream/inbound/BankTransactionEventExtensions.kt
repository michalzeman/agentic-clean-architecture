package mz.bank.account.adapter.redis.stream.inbound

import mz.bank.account.application.integration.inbound.InboundBankTransactionEvent
import mz.bank.transaction.contract.proto.BankTransactionEvent
import mz.shared.domain.AggregateId
import java.math.BigDecimal
import java.time.Instant

/**
 * Converts a protobuf BankTransactionEvent to InboundBankTransactionEvent.
 * Returns a Default event for empty or unrecognized event types.
 */
fun BankTransactionEvent.toInboundEvent(): InboundBankTransactionEvent =
    when {
        hasTransactionCreated() -> {
            val event = transactionCreated
            InboundBankTransactionEvent.TransactionCreated(
                aggregateId = AggregateId(event.aggregateId),
                correlationId = event.correlationId,
                updatedAt = Instant.ofEpochMilli(event.updatedAtEpochMillis),
                fromAccountId = AggregateId(event.fromAccountId),
                toAccountId = AggregateId(event.toAccountId),
                amount = BigDecimal(event.amount),
            )
        }

        hasTransactionMoneyWithdrawn() -> {
            val event = transactionMoneyWithdrawn
            InboundBankTransactionEvent.TransactionMoneyWithdrawn(
                aggregateId = AggregateId(event.aggregateId),
                correlationId = event.correlationId,
                updatedAt = Instant.ofEpochMilli(event.updatedAtEpochMillis),
                accountId = AggregateId(event.accountId),
            )
        }

        hasTransactionMoneyDeposited() -> {
            val event = transactionMoneyDeposited
            InboundBankTransactionEvent.TransactionMoneyDeposited(
                aggregateId = AggregateId(event.aggregateId),
                correlationId = event.correlationId,
                updatedAt = Instant.ofEpochMilli(event.updatedAtEpochMillis),
                accountId = AggregateId(event.accountId),
            )
        }

        hasTransactionFinished() -> {
            val event = transactionFinished
            InboundBankTransactionEvent.TransactionFinished(
                aggregateId = AggregateId(event.aggregateId),
                correlationId = event.correlationId,
                updatedAt = Instant.ofEpochMilli(event.updatedAtEpochMillis),
                fromAccountId = AggregateId(event.fromAccountId),
                toAccountId = AggregateId(event.toAccountId),
            )
        }

        hasTransactionFailed() -> {
            val event = transactionFailed
            InboundBankTransactionEvent.TransactionFailed(
                aggregateId = AggregateId(event.aggregateId),
                correlationId = event.correlationId,
                updatedAt = Instant.ofEpochMilli(event.updatedAtEpochMillis),
                reason = event.reason,
            )
        }

        hasTransactionRolledBack() -> {
            val event = transactionRolledBack
            InboundBankTransactionEvent.TransactionRolledBack(
                aggregateId = AggregateId(event.aggregateId),
                correlationId = event.correlationId,
                updatedAt = Instant.ofEpochMilli(event.updatedAtEpochMillis),
                fromAccountId = AggregateId(event.fromAccountId),
                toAccountId = AggregateId(event.toAccountId),
                amount = BigDecimal(event.amount),
            )
        }

        hasTransactionWithdrawRolledBack() -> {
            val event = transactionWithdrawRolledBack
            InboundBankTransactionEvent.TransactionWithdrawRolledBack(
                aggregateId = AggregateId(event.aggregateId),
                correlationId = event.correlationId,
                updatedAt = Instant.ofEpochMilli(event.updatedAtEpochMillis),
            )
        }

        hasTransactionDepositRolledBack() -> {
            val event = transactionDepositRolledBack
            InboundBankTransactionEvent.TransactionDepositRolledBack(
                aggregateId = AggregateId(event.aggregateId),
                correlationId = event.correlationId,
                updatedAt = Instant.ofEpochMilli(event.updatedAtEpochMillis),
            )
        }

        else ->
            InboundBankTransactionEvent.Default(
                aggregateId = AggregateId("unknown"),
                correlationId = "unknown",
                updatedAt = Instant.now(),
            )
    }
