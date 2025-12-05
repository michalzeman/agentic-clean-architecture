package mz.bank.account.domain

import mz.shared.domain.AggregateId
import java.math.BigDecimal
import java.time.Instant

/**
 * BankAccountAggregate is the aggregate root that manages the bank account state and business logic.
 * It processes commands and produces domain events following the Event Sourcing pattern.
 */
data class BankAccountAggregate(
    val account: BankAccount,
    val domainEvents: List<BankAccountEvent> = emptyList(),
) {
    /**
     * Applies a single event to rebuild state (used for event sourcing replay).
     * Returns new aggregate with updated state, no new domain events.
     */
    fun applyEvent(event: BankAccountEvent): BankAccountAggregate {
        val updatedAccount =
            when (event) {
                is BankAccountEvent.AccountCreated -> {
                    account.copy(
                        amount = event.initialBalance,
                        version = account.version + 1,
                        updatedAt = event.updatedAt,
                    )
                }
                is BankAccountEvent.MoneyDeposited -> {
                    account.copy(
                        amount = account.amount + event.amount,
                        version = account.version + 1,
                        updatedAt = event.updatedAt,
                    )
                }
                is BankAccountEvent.MoneyWithdrawn -> {
                    account.copy(
                        amount = account.amount - event.amount,
                        version = account.version + 1,
                        updatedAt = event.updatedAt,
                    )
                }
                is BankAccountEvent.TransferWithdrawalStarted -> {
                    account.copy(
                        amount = account.amount - event.amount,
                        openedTransactions = account.openedTransactions + event.transactionId,
                        version = account.version + 1,
                        updatedAt = event.updatedAt,
                    )
                }
                is BankAccountEvent.TransferDepositStarted -> {
                    account.copy(
                        amount = account.amount + event.amount,
                        openedTransactions = account.openedTransactions + event.transactionId,
                        version = account.version + 1,
                        updatedAt = event.updatedAt,
                    )
                }
                is BankAccountEvent.TransactionFinished -> {
                    account.copy(
                        openedTransactions = account.openedTransactions - event.transactionId,
                        finishedTransactions = account.finishedTransactions + event.transactionId,
                        version = account.version + 1,
                        updatedAt = event.updatedAt,
                    )
                }
            }

        return copy(account = updatedAccount)
    }

    /**
     * Deposits money into the account.
     */
    fun deposit(cmd: BankAccountCommand.DepositMoney): BankAccountAggregate {
        require(cmd.amount > BigDecimal.ZERO) {
            "Deposit amount must be positive"
        }

        val event =
            BankAccountEvent.MoneyDeposited(
                aggregateId = cmd.aggregateId,
                updatedAt = Instant.now(),
                amount = cmd.amount,
            )

        return applyEvent(event).copy(domainEvents = listOf(event))
    }

    /**
     * Withdraws money from the account.
     */
    fun withdraw(cmd: BankAccountCommand.WithdrawMoney): BankAccountAggregate {
        require(cmd.amount > BigDecimal.ZERO) {
            "Withdrawal amount must be positive"
        }
        require(account.amount >= cmd.amount) {
            "Insufficient balance: required ${cmd.amount}, available ${account.amount}"
        }

        val event =
            BankAccountEvent.MoneyWithdrawn(
                aggregateId = cmd.aggregateId,
                updatedAt = Instant.now(),
                amount = cmd.amount,
            )

        return applyEvent(event).copy(domainEvents = listOf(event))
    }

    /**
     * Withdraws money for a transfer transaction.
     */
    fun withdrawForTransfer(cmd: BankAccountCommand.WithdrawForTransfer): BankAccountAggregate {
        require(account.amount >= cmd.amount) {
            "Insufficient balance for transfer: required ${cmd.amount}, available ${account.amount}"
        }
        require(cmd.transactionId !in account.openedTransactions) {
            "Transaction ${cmd.transactionId} is already opened"
        }
        require(cmd.transactionId !in account.finishedTransactions) {
            "Transaction ${cmd.transactionId} is already finished"
        }

        val event =
            BankAccountEvent.TransferWithdrawalStarted(
                aggregateId = cmd.aggregateId,
                updatedAt = Instant.now(),
                transactionId = cmd.transactionId,
                amount = cmd.amount,
            )

        return applyEvent(event).copy(domainEvents = listOf(event))
    }

    /**
     * Deposits money from a transfer transaction.
     */
    fun depositFromTransfer(cmd: BankAccountCommand.DepositFromTransfer): BankAccountAggregate {
        require(cmd.transactionId !in account.openedTransactions) {
            "Transaction ${cmd.transactionId} is already opened"
        }
        require(cmd.transactionId !in account.finishedTransactions) {
            "Transaction ${cmd.transactionId} is already finished"
        }

        val event =
            BankAccountEvent.TransferDepositStarted(
                aggregateId = cmd.aggregateId,
                updatedAt = Instant.now(),
                transactionId = cmd.transactionId,
                amount = cmd.amount,
            )

        return applyEvent(event).copy(domainEvents = listOf(event))
    }

    /**
     * Finishes a transaction.
     */
    fun finishTransaction(cmd: BankAccountCommand.FinishTransaction): BankAccountAggregate {
        require(cmd.transactionId in account.openedTransactions) {
            "Transaction ${cmd.transactionId} is not in opened set"
        }

        val event =
            BankAccountEvent.TransactionFinished(
                aggregateId = cmd.aggregateId,
                updatedAt = Instant.now(),
                transactionId = cmd.transactionId,
            )

        return applyEvent(event).copy(domainEvents = listOf(event))
    }

    companion object {
        /**
         * Creates a new bank account with initial balance.
         */
        fun create(cmd: BankAccountCommand.CreateAccount): BankAccountAggregate {
            require(cmd.initialBalance >= BigDecimal.ZERO) {
                "Initial balance cannot be negative"
            }

            val now = Instant.now()
            val newAccount =
                BankAccount(
                    aggregateId = cmd.aggregateId,
                    amount = cmd.initialBalance,
                    openedTransactions = emptySet(),
                    finishedTransactions = emptySet(),
                    version = 0L,
                    createdAt = now,
                    updatedAt = now,
                )

            val event =
                BankAccountEvent.AccountCreated(
                    aggregateId = cmd.aggregateId,
                    updatedAt = now,
                    initialBalance = cmd.initialBalance,
                )

            return BankAccountAggregate(
                account = newAccount,
                domainEvents = listOf(event),
            )
        }

        /**
         * Rebuilds aggregate state from events using fold (functional approach).
         */
        fun fromEvents(
            aggregateId: AggregateId,
            events: List<BankAccountEvent>,
        ): BankAccountAggregate {
            require(events.isNotEmpty()) { "Events list must not be empty" }

            val initialAggregate =
                BankAccountAggregate(
                    account =
                        BankAccount(
                            aggregateId = aggregateId,
                            amount = BigDecimal.ZERO,
                            openedTransactions = emptySet(),
                            finishedTransactions = emptySet(),
                            version = 0L,
                            createdAt = Instant.now(),
                            updatedAt = Instant.now(),
                        ),
                )

            return events.fold(initialAggregate) { aggregate, event ->
                aggregate.applyEvent(event)
            }
        }
    }
}
