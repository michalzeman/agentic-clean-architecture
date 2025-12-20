package mz.bank.transaction.domain

import mz.shared.domain.AggregateId
import java.time.Instant
import java.util.UUID

/**
 * TransactionAggregate is the aggregate root that manages the transaction state and business logic.
 * It processes commands and produces domain events following the Event Sourcing pattern.
 * Implements the saga pattern for distributed transactions across two accounts.
 */
data class TransactionAggregate(
    val transaction: Transaction,
    val domainEvents: List<TransactionEvent> = emptyList(),
) {
    /**
     * Applies a single event to rebuild state (used for event sourcing replay).
     * Returns a new aggregate with an updated state, no new domain events.
     */
    fun applyEvent(event: TransactionEvent): TransactionAggregate {
        val updatedTransaction =
            when (event) {
                is TransactionEvent.TransactionCreated -> {
                    transaction.copy(
                        fromAccountId = event.fromAccountId,
                        toAccountId = event.toAccountId,
                        amount = event.amount,
                        status = TransactionStatus.CREATED,
                        version = transaction.version + 1,
                        updatedAt = event.updatedAt,
                    )
                }
                is TransactionEvent.TransactionMoneyWithdrawn -> {
                    transaction.copy(
                        moneyWithdrawn = true,
                        version = transaction.version + 1,
                        updatedAt = event.updatedAt,
                    )
                }
                is TransactionEvent.TransactionMoneyDeposited -> {
                    transaction.copy(
                        moneyDeposited = true,
                        version = transaction.version + 1,
                        updatedAt = event.updatedAt,
                    )
                }
                is TransactionEvent.TransactionFinished -> {
                    transaction.copy(
                        status = TransactionStatus.FINISHED,
                        version = transaction.version + 1,
                        updatedAt = event.updatedAt,
                    )
                }
                is TransactionEvent.TransactionFailed -> {
                    transaction.copy(
                        status = TransactionStatus.FAILED,
                        version = transaction.version + 1,
                        updatedAt = event.updatedAt,
                    )
                }
                is TransactionEvent.TransactionRolledBack -> {
                    transaction.copy(
                        status = TransactionStatus.FAILED,
                        version = transaction.version + 1,
                        updatedAt = event.updatedAt,
                    )
                }
                is TransactionEvent.TransactionWithdrawRolledBack -> {
                    transaction.copy(
                        moneyWithdrawn = false,
                        version = transaction.version + 1,
                        updatedAt = event.updatedAt,
                    )
                }
                is TransactionEvent.TransactionDepositRolledBack -> {
                    transaction.copy(
                        moneyDeposited = false,
                        version = transaction.version + 1,
                        updatedAt = event.updatedAt,
                    )
                }
            }

        return copy(transaction = updatedTransaction)
    }

    /**
     * Validates that money was withdrawn from the source account.
     */
    fun validateMoneyWithdraw(cmd: TransactionCommand.ValidateTransactionMoneyWithdraw): TransactionAggregate {
        require(transaction.status == TransactionStatus.CREATED) {
            "Cannot validate withdraw for transaction in status ${transaction.status}"
        }

        val event =
            TransactionEvent.TransactionMoneyWithdrawn(
                aggregateId = cmd.aggregateId,
                correlationId = cmd.correlationId,
                updatedAt = Instant.now(),
            )

        return applyEvent(event).copy(domainEvents = listOf(event))
    }

    /**
     * Validates that money was deposited to the destination account.
     */
    fun validateMoneyDeposit(cmd: TransactionCommand.ValidateTransactionMoneyDeposit): TransactionAggregate {
        require(transaction.status == TransactionStatus.CREATED) {
            "Cannot validate deposit for transaction in status ${transaction.status}"
        }
        require(transaction.moneyWithdrawn) {
            "Cannot validate deposit before money is withdrawn"
        }

        val event =
            TransactionEvent.TransactionMoneyDeposited(
                aggregateId = cmd.aggregateId,
                correlationId = cmd.correlationId,
                updatedAt = Instant.now(),
            )

        return applyEvent(event).copy(domainEvents = listOf(event))
    }

    /**
     * Completes the transaction after both withdraw and deposit are done.
     */
    fun finishTransaction(cmd: TransactionCommand.FinishTransaction): TransactionAggregate {
        require(transaction.status != TransactionStatus.FAILED) {
            "Cannot finish a failed transaction"
        }
        require(transaction.moneyWithdrawn && transaction.moneyDeposited) {
            "Cannot finish transaction: both withdraw and deposit must be completed"
        }

        val event =
            TransactionEvent.TransactionFinished(
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
    fun cancelTransaction(cmd: TransactionCommand.CancelTransaction): TransactionAggregate {
        require(transaction.status != TransactionStatus.FINISHED) {
            "Cannot cancel a finished transaction"
        }

        val events = mutableListOf<TransactionEvent>()

        // Rollback deposit if it was completed
        if (transaction.moneyDeposited) {
            events.add(
                TransactionEvent.TransactionDepositRolledBack(
                    aggregateId = cmd.aggregateId,
                    correlationId = cmd.correlationId,
                    updatedAt = Instant.now(),
                ),
            )
        }

        // Rollback withdraw if it was completed
        if (transaction.moneyWithdrawn) {
            events.add(
                TransactionEvent.TransactionWithdrawRolledBack(
                    aggregateId = cmd.aggregateId,
                    correlationId = cmd.correlationId,
                    updatedAt = Instant.now(),
                ),
            )
        }

        // Mark transaction as rolled back
        events.add(
            TransactionEvent.TransactionRolledBack(
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
        fun create(cmd: TransactionCommand.CreateTransaction): TransactionAggregate {
            require(cmd.amount > java.math.BigDecimal.ZERO) {
                "Transaction amount must be positive"
            }

            val now = Instant.now()
            val aggregateId = AggregateId(UUID.randomUUID().toString())
            val newTransaction =
                Transaction(
                    aggregateId = aggregateId,
                    correlationId = cmd.correlationId,
                    fromAccountId = cmd.fromAccountId,
                    toAccountId = cmd.toAccountId,
                    amount = cmd.amount,
                    moneyWithdrawn = false,
                    moneyDeposited = false,
                    status = TransactionStatus.INITIALIZED,
                    version = 0L,
                    createdAt = now,
                    updatedAt = now,
                )

            val event =
                TransactionEvent.TransactionCreated(
                    aggregateId = aggregateId,
                    correlationId = cmd.correlationId,
                    updatedAt = now,
                    fromAccountId = cmd.fromAccountId,
                    toAccountId = cmd.toAccountId,
                    amount = cmd.amount,
                )

            // Apply event to move to CREATED status
            val aggregate =
                TransactionAggregate(
                    transaction = newTransaction,
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
            events: List<TransactionEvent>,
        ): TransactionAggregate {
            require(events.isNotEmpty()) { "Events list must not be empty" }
            require(events.first() is TransactionEvent.TransactionCreated) {
                "First event must be TransactionCreated"
            }

            // Get initial data from the first event
            val createdEvent = events.first() as TransactionEvent.TransactionCreated

            // Create initial aggregate with data from TransactionCreated event
            val initialAggregate =
                TransactionAggregate(
                    transaction =
                        Transaction(
                            aggregateId = aggregateId,
                            correlationId = correlationId,
                            fromAccountId = createdEvent.fromAccountId,
                            toAccountId = createdEvent.toAccountId,
                            amount = createdEvent.amount,
                            moneyWithdrawn = false,
                            moneyDeposited = false,
                            status = TransactionStatus.INITIALIZED,
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
