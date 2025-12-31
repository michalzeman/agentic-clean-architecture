package mz.bank.transaction.adapter.redis.stream.inbound

import mz.bank.account.contract.proto.BankAccountEvent
import mz.bank.transaction.application.account.AccountEvent
import mz.shared.domain.AggregateId
import java.math.BigDecimal

/**
 * Converts a protobuf BankAccountEvent to an application AccountEvent.
 * Maps all relevant bank account events to their corresponding application event types.
 */
fun BankAccountEvent.toAccountEvent(): AccountEvent =
    when {
        hasAccountCreated() -> {
            val event = accountCreated
            AccountEvent.AccountCreatedEvent(
                accountId = AggregateId(event.aggregateId),
            )
        }

        hasTransferWithdrawalStarted() -> {
            val event = transferWithdrawalStarted
            AccountEvent.TransferWithdrawalStartedEvent(
                accountId = AggregateId(event.aggregateId),
                transactionId = event.transactionId,
                amount = BigDecimal(event.amount),
            )
        }

        hasTransferDepositStarted() -> {
            val event = transferDepositStarted
            AccountEvent.TransferDepositStartedEvent(
                accountId = AggregateId(event.aggregateId),
                transactionId = event.transactionId,
                amount = BigDecimal(event.amount),
            )
        }

        hasTransactionFinished() -> {
            val event = transactionFinished
            AccountEvent.TransactionFinishedEvent(
                accountId = AggregateId(event.aggregateId),
                transactionId = event.transactionId,
            )
        }

        hasTransferWithdrawalRolledBack() -> {
            val event = transferWithdrawalRolledBack
            AccountEvent.TransferWithdrawalRolledBackEvent(
                accountId = AggregateId(event.aggregateId),
                transactionId = event.transactionId,
                amount = BigDecimal(event.amount),
            )
        }

        hasTransferDepositRolledBack() -> {
            val event = transferDepositRolledBack
            AccountEvent.TransferDepositRolledBackEvent(
                accountId = AggregateId(event.aggregateId),
                transactionId = event.transactionId,
                amount = BigDecimal(event.amount),
            )
        }

        else -> AccountEvent.DefaultAccountEvent
    }
