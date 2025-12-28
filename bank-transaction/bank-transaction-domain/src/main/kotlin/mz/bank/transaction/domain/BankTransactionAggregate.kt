package mz.bank.transaction.domain

import mz.shared.domain.AggregateId
import java.time.Instant
import java.util.UUID

/**
 * BankTransactionAggregate is the aggregate root that manages the transaction state and business logic.
 * It processes commands and produces domain events following the Event Sourcing pattern.
 * Implements the saga pattern for distributed transactions across two accounts.
 */
data class BankTransactionAggregate(
    val bankTransaction: BankTransaction,
    val domainEvents: List<BankTransactionEvent> = emptyList(),
) {
    /**
     * Applies a single event to rebuild state (used for event sourcing replay).
     * Returns a new aggregate with an updated state, no new domain events.
     */
    fun applyEvent(event: BankTransactionEvent): BankTransactionAggregate {
        val updatedBankTransaction =
            when (event) {
                is BankTransactionEvent.BankTransactionCreated -> {
                    bankTransaction.copy(
                        fromAccountId = event.fromAccountId,
                        toAccountId = event.toAccountId,
                        amount = event.amount,
                        status = BankTransactionStatus.CREATED,
                        version = bankTransaction.version + 1,
                        updatedAt = event.updatedAt,
                    )
                }
                is BankTransactionEvent.BankTransactionMoneyWithdrawn -> {
                    bankTransaction.copy(
                        moneyWithdrawn = true,
                        version = bankTransaction.version + 1,
                        updatedAt = event.updatedAt,
                    )
                }
                is BankTransactionEvent.BankTransactionMoneyDeposited -> {
                    bankTransaction.copy(
                        moneyDeposited = true,
                        version = bankTransaction.version + 1,
                        updatedAt = event.updatedAt,
                    )
                }
                is BankTransactionEvent.BankTransactionFinished -> {
                    bankTransaction.copy(
                        moneyWithdrawn = true,
                        moneyDeposited = true,
                        status = BankTransactionStatus.FINISHED,
                        version = bankTransaction.version + 1,
                        updatedAt = event.updatedAt,
                    )
                }
                is BankTransactionEvent.BankTransactionFailed -> {
                    bankTransaction.copy(
                        status = BankTransactionStatus.FAILED,
                        version = bankTransaction.version + 1,
                        updatedAt = event.updatedAt,
                    )
                }
                is BankTransactionEvent.BankTransactionRolledBack -> {
                    bankTransaction.copy(
                        status = BankTransactionStatus.FAILED,
                        version = bankTransaction.version + 1,
                        updatedAt = event.updatedAt,
                    )
                }
                is BankTransactionEvent.BankTransactionWithdrawRolledBack -> {
                    bankTransaction.copy(
                        moneyWithdrawn = false,
                        version = bankTransaction.version + 1,
                        updatedAt = event.updatedAt,
                    )
                }
                is BankTransactionEvent.BankTransactionDepositRolledBack -> {
                    bankTransaction.copy(
                        moneyDeposited = false,
                        version = bankTransaction.version + 1,
                        updatedAt = event.updatedAt,
                    )
                }
            }

        return copy(bankTransaction = updatedBankTransaction)
    }

    /**
     * Validates that money was withdrawn from the source account.
     */
    fun validateMoneyWithdraw(cmd: BankTransactionCommand.ValidateBankTransactionMoneyWithdraw): BankTransactionAggregate {
        require(bankTransaction.status != BankTransactionStatus.FINISHED && bankTransaction.status != BankTransactionStatus.FAILED) {
            "Cannot validate withdraw for bankTransaction in status ${bankTransaction.status}"
        }
        require(cmd.accountId == bankTransaction.fromAccountId) {
            "AccountId ${cmd.accountId.value} does not match transaction's fromAccountId ${bankTransaction.fromAccountId.value}"
        }

        val event =
            if (bankTransaction.moneyDeposited) {
                BankTransactionEvent.BankTransactionFinished(
                    aggregateId = cmd.aggregateId,
                    correlationId = cmd.correlationId,
                    updatedAt = Instant.now(),
                    fromAccountId = bankTransaction.fromAccountId,
                    toAccountId = bankTransaction.toAccountId,
                )
            } else {
                BankTransactionEvent.BankTransactionMoneyWithdrawn(
                    aggregateId = cmd.aggregateId,
                    correlationId = cmd.correlationId,
                    updatedAt = Instant.now(),
                    accountId = cmd.accountId,
                )
            }

        return applyEvent(event).copy(domainEvents = listOf(event))
    }

    /**
     * Validates that money was deposited to the destination account.
     */
    fun validateMoneyDeposit(cmd: BankTransactionCommand.ValidateBankTransactionMoneyDeposit): BankTransactionAggregate {
        require(bankTransaction.status != BankTransactionStatus.FINISHED && bankTransaction.status != BankTransactionStatus.FAILED) {
            "Cannot validate deposit for bankTransaction in status ${bankTransaction.status}"
        }
        require(cmd.accountId == bankTransaction.toAccountId) {
            "AccountId ${cmd.accountId.value} does not match transaction's toAccountId ${bankTransaction.toAccountId.value}"
        }

        val event =
            if (bankTransaction.moneyWithdrawn) {
                BankTransactionEvent.BankTransactionFinished(
                    aggregateId = cmd.aggregateId,
                    correlationId = cmd.correlationId,
                    updatedAt = Instant.now(),
                    fromAccountId = bankTransaction.fromAccountId,
                    toAccountId = bankTransaction.toAccountId,
                )
            } else {
                BankTransactionEvent.BankTransactionMoneyDeposited(
                    aggregateId = cmd.aggregateId,
                    correlationId = cmd.correlationId,
                    updatedAt = Instant.now(),
                    accountId = cmd.accountId,
                )
            }

        return applyEvent(event).copy(domainEvents = listOf(event))
    }

    /**
     * Completes the transaction after both withdraw and deposit are done.
     */
    fun finishBankTransaction(cmd: BankTransactionCommand.FinishBankTransaction): BankTransactionAggregate {
        require(bankTransaction.status != BankTransactionStatus.FAILED) {
            "Cannot finish a failed transaction"
        }
        require(bankTransaction.moneyWithdrawn && bankTransaction.moneyDeposited) {
            "Cannot finish transaction: both withdraw and deposit must be completed"
        }

        val event =
            BankTransactionEvent.BankTransactionFinished(
                aggregateId = cmd.aggregateId,
                correlationId = cmd.correlationId,
                updatedAt = Instant.now(),
                fromAccountId = cmd.fromAccountId,
                toAccountId = cmd.toAccountId,
            )

        return applyEvent(event).copy(domainEvents = listOf(event))
    }

    /**
     * Cancels and rolls back the transaction.
     */
    fun cancelBankTransaction(cmd: BankTransactionCommand.CancelBankTransaction): BankTransactionAggregate {
        require(bankTransaction.status != BankTransactionStatus.FINISHED) {
            "Cannot cancel a finished transaction"
        }

        val events = mutableListOf<BankTransactionEvent>()

        // Rollback deposit if it was completed
        if (bankTransaction.moneyDeposited) {
            events.add(
                BankTransactionEvent.BankTransactionDepositRolledBack(
                    aggregateId = cmd.aggregateId,
                    correlationId = cmd.correlationId,
                    updatedAt = Instant.now(),
                ),
            )
        }

        // Rollback withdraw if it was completed
        if (bankTransaction.moneyWithdrawn) {
            events.add(
                BankTransactionEvent.BankTransactionWithdrawRolledBack(
                    aggregateId = cmd.aggregateId,
                    correlationId = cmd.correlationId,
                    updatedAt = Instant.now(),
                ),
            )
        }

        // Mark transaction as rolled back
        events.add(
            BankTransactionEvent.BankTransactionRolledBack(
                aggregateId = cmd.aggregateId,
                correlationId = cmd.correlationId,
                updatedAt = Instant.now(),
                fromAccountId = cmd.fromAccountId,
                toAccountId = cmd.toAccountId,
                amount = cmd.amount,
            ),
        )

        // Apply all events to update state
        val finalAggregate =
            events.fold(this) { aggregate, event ->
                aggregate.applyEvent(event)
            }

        return finalAggregate.copy(domainEvents = events)
    }

    companion object {
        /**
         * Creates a new transaction with initial status CREATED.
         * Generates a unique UUID for the aggregate ID.
         */
        fun create(cmd: BankTransactionCommand.CreateBankTransaction): BankTransactionAggregate {
            val now = Instant.now()
            val aggregateId = AggregateId(UUID.randomUUID().toString())
            val newBankTransaction =
                BankTransaction(
                    aggregateId = aggregateId,
                    correlationId = cmd.correlationId,
                    fromAccountId = cmd.fromAccountId,
                    toAccountId = cmd.toAccountId,
                    amount = cmd.amount,
                    moneyWithdrawn = false,
                    moneyDeposited = false,
                    status = BankTransactionStatus.INITIALIZED,
                    version = 0L,
                    createdAt = now,
                    updatedAt = now,
                )

            val event =
                BankTransactionEvent.BankTransactionCreated(
                    aggregateId = aggregateId,
                    correlationId = cmd.correlationId,
                    updatedAt = now,
                    fromAccountId = cmd.fromAccountId,
                    toAccountId = cmd.toAccountId,
                    amount = cmd.amount,
                )

            // Apply event to move to CREATED status
            val aggregate =
                BankTransactionAggregate(
                    bankTransaction = newBankTransaction,
                    domainEvents = emptyList(),
                )

            return aggregate.applyEvent(event).copy(domainEvents = listOf(event))
        }

        /**
         * Rebuilds aggregate state from events using fold (functional approach).
         */
        fun fromEvents(
            aggregateId: AggregateId,
            correlationId: String,
            events: List<BankTransactionEvent>,
        ): BankTransactionAggregate {
            require(events.isNotEmpty()) { "Events list must not be empty" }
            require(events.first() is BankTransactionEvent.BankTransactionCreated) {
                "First event must be BankTransactionCreated"
            }

            // Get initial data from the first event
            val createdEvent = events.first() as BankTransactionEvent.BankTransactionCreated

            // Create initial aggregate with data from BankTransactionCreated event
            val initialAggregate =
                BankTransactionAggregate(
                    bankTransaction =
                        BankTransaction(
                            aggregateId = aggregateId,
                            correlationId = correlationId,
                            fromAccountId = createdEvent.fromAccountId,
                            toAccountId = createdEvent.toAccountId,
                            amount = createdEvent.amount,
                            moneyWithdrawn = false,
                            moneyDeposited = false,
                            status = BankTransactionStatus.INITIALIZED,
                            version = 0L,
                            createdAt = createdEvent.updatedAt,
                            updatedAt = createdEvent.updatedAt,
                        ),
                )

            return events.fold(initialAggregate) { aggregate, event ->
                aggregate.applyEvent(event)
            }
        }
    }
}
