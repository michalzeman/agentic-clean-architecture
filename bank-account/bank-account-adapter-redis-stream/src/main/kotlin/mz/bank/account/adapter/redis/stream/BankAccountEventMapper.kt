package mz.bank.account.adapter.redis.stream

import mz.bank.account.contract.proto.BankAccountEvent
import mz.bank.account.contract.proto.accountCreated
import mz.bank.account.contract.proto.bankAccountEvent
import mz.bank.account.contract.proto.moneyDeposited
import mz.bank.account.contract.proto.moneyWithdrawn
import mz.bank.account.contract.proto.transactionFinished
import mz.bank.account.contract.proto.transferDepositStarted
import mz.bank.account.contract.proto.transferWithdrawalStarted
import mz.bank.account.domain.BankAccountEvent as DomainEvent

/**
 * Mapper for converting domain events to protobuf events.
 */
object BankAccountEventMapper {
    /**
     * Converts a domain BankAccountEvent to a protobuf BankAccountEvent.
     */
    fun toProto(domainEvent: DomainEvent): BankAccountEvent =
        when (domainEvent) {
            is DomainEvent.AccountCreated ->
                bankAccountEvent {
                    accountCreated =
                        accountCreated {
                            aggregateId = domainEvent.aggregateId.value
                            updatedAtEpochMillis = domainEvent.updatedAt.toEpochMilli()
                            email = domainEvent.email.value
                            initialBalance = domainEvent.initialBalance.toPlainString()
                        }
                }
            is DomainEvent.MoneyDeposited ->
                bankAccountEvent {
                    moneyDeposited =
                        moneyDeposited {
                            aggregateId = domainEvent.aggregateId.value
                            updatedAtEpochMillis = domainEvent.updatedAt.toEpochMilli()
                            amount = domainEvent.amount.toPlainString()
                        }
                }
            is DomainEvent.MoneyWithdrawn ->
                bankAccountEvent {
                    moneyWithdrawn =
                        moneyWithdrawn {
                            aggregateId = domainEvent.aggregateId.value
                            updatedAtEpochMillis = domainEvent.updatedAt.toEpochMilli()
                            amount = domainEvent.amount.toPlainString()
                        }
                }
            is DomainEvent.TransferWithdrawalStarted ->
                bankAccountEvent {
                    transferWithdrawalStarted =
                        transferWithdrawalStarted {
                            aggregateId = domainEvent.aggregateId.value
                            updatedAtEpochMillis = domainEvent.updatedAt.toEpochMilli()
                            transactionId = domainEvent.transactionId
                            amount = domainEvent.amount.toPlainString()
                        }
                }
            is DomainEvent.TransferDepositStarted ->
                bankAccountEvent {
                    transferDepositStarted =
                        transferDepositStarted {
                            aggregateId = domainEvent.aggregateId.value
                            updatedAtEpochMillis = domainEvent.updatedAt.toEpochMilli()
                            transactionId = domainEvent.transactionId
                            amount = domainEvent.amount.toPlainString()
                        }
                }
            is DomainEvent.TransactionFinished ->
                bankAccountEvent {
                    transactionFinished =
                        transactionFinished {
                            aggregateId = domainEvent.aggregateId.value
                            updatedAtEpochMillis = domainEvent.updatedAt.toEpochMilli()
                            transactionId = domainEvent.transactionId
                        }
                }
        }
}
