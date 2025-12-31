package mz.bank.transaction.adapter.redis.stream

import mz.bank.transaction.contract.proto.BankTransactionEvent
import mz.bank.transaction.contract.proto.bankTransactionEvent
import mz.bank.transaction.contract.proto.transactionCreated
import mz.bank.transaction.contract.proto.transactionDepositRolledBack
import mz.bank.transaction.contract.proto.transactionFailed
import mz.bank.transaction.contract.proto.transactionFinished
import mz.bank.transaction.contract.proto.transactionMoneyDeposited
import mz.bank.transaction.contract.proto.transactionMoneyWithdrawn
import mz.bank.transaction.contract.proto.transactionRolledBack
import mz.bank.transaction.contract.proto.transactionWithdrawRolledBack
import mz.bank.transaction.domain.BankTransactionEvent as DomainEvent

/**
 * Mapper for converting domain events to protobuf events.
 */
object BankTransactionEventMapper {
    /**
     * Converts a domain BankTransactionEvent to a protobuf BankTransactionEvent.
     */
    fun toProto(domainEvent: DomainEvent): BankTransactionEvent =
        when (domainEvent) {
            is DomainEvent.BankTransactionCreated ->
                bankTransactionEvent {
                    transactionCreated =
                        transactionCreated {
                            aggregateId = domainEvent.aggregateId.value
                            correlationId = domainEvent.correlationId
                            updatedAtEpochMillis = domainEvent.updatedAt.toEpochMilli()
                            fromAccountId = domainEvent.fromAccountId.value
                            toAccountId = domainEvent.toAccountId.value
                            amount = domainEvent.amount.toPlainString()
                        }
                }
            is DomainEvent.BankTransactionMoneyWithdrawn ->
                bankTransactionEvent {
                    transactionMoneyWithdrawn =
                        transactionMoneyWithdrawn {
                            aggregateId = domainEvent.aggregateId.value
                            correlationId = domainEvent.correlationId
                            updatedAtEpochMillis = domainEvent.updatedAt.toEpochMilli()
                            accountId = domainEvent.accountId.value
                            toAccountId = domainEvent.toAccountId.value
                            amount = domainEvent.amount.toPlainString()
                        }
                }
            is DomainEvent.BankTransactionMoneyDeposited ->
                bankTransactionEvent {
                    transactionMoneyDeposited =
                        transactionMoneyDeposited {
                            aggregateId = domainEvent.aggregateId.value
                            correlationId = domainEvent.correlationId
                            updatedAtEpochMillis = domainEvent.updatedAt.toEpochMilli()
                            accountId = domainEvent.accountId.value
                        }
                }
            is DomainEvent.BankTransactionFinished ->
                bankTransactionEvent {
                    transactionFinished =
                        transactionFinished {
                            aggregateId = domainEvent.aggregateId.value
                            correlationId = domainEvent.correlationId
                            updatedAtEpochMillis = domainEvent.updatedAt.toEpochMilli()
                            fromAccountId = domainEvent.fromAccountId.value
                            toAccountId = domainEvent.toAccountId.value
                        }
                }
            is DomainEvent.BankTransactionFailed ->
                bankTransactionEvent {
                    transactionFailed =
                        transactionFailed {
                            aggregateId = domainEvent.aggregateId.value
                            correlationId = domainEvent.correlationId
                            updatedAtEpochMillis = domainEvent.updatedAt.toEpochMilli()
                            fromAccountId = domainEvent.fromAccountId.value
                            toAccountId = domainEvent.toAccountId.value
                            amount = domainEvent.amount.toPlainString()
                            reason = domainEvent.reason
                        }
                }
            is DomainEvent.BankTransactionRolledBack ->
                bankTransactionEvent {
                    transactionRolledBack =
                        transactionRolledBack {
                            aggregateId = domainEvent.aggregateId.value
                            correlationId = domainEvent.correlationId
                            updatedAtEpochMillis = domainEvent.updatedAt.toEpochMilli()
                            fromAccountId = domainEvent.fromAccountId.value
                            toAccountId = domainEvent.toAccountId.value
                            amount = domainEvent.amount.toPlainString()
                        }
                }
            is DomainEvent.BankTransactionWithdrawRolledBack ->
                bankTransactionEvent {
                    transactionWithdrawRolledBack =
                        transactionWithdrawRolledBack {
                            aggregateId = domainEvent.aggregateId.value
                            correlationId = domainEvent.correlationId
                            updatedAtEpochMillis = domainEvent.updatedAt.toEpochMilli()
                            fromAccountId = domainEvent.fromAccountId.value
                            toAccountId = domainEvent.toAccountId.value
                            amount = domainEvent.amount.toPlainString()
                        }
                }
            is DomainEvent.BankTransactionDepositRolledBack ->
                bankTransactionEvent {
                    transactionDepositRolledBack =
                        transactionDepositRolledBack {
                            aggregateId = domainEvent.aggregateId.value
                            correlationId = domainEvent.correlationId
                            updatedAtEpochMillis = domainEvent.updatedAt.toEpochMilli()
                            fromAccountId = domainEvent.fromAccountId.value
                            toAccountId = domainEvent.toAccountId.value
                            amount = domainEvent.amount.toPlainString()
                        }
                }
        }
}
