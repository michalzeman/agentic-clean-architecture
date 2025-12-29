package mz.bank.account.application.integration.inbound

import mz.shared.domain.AggregateId
import java.math.BigDecimal
import java.time.Instant

/**
 * Domain representation of bank transaction events received from the bank-transaction service.
 * These events are consumed via Redis streams and used to update the bank account state.
 */
sealed class InboundBankTransactionEvent {
    abstract val aggregateId: AggregateId
    abstract val correlationId: String
    abstract val updatedAt: Instant

    /**
     * Default event type for events that are not applicable or unrecognized.
     */
    data class Default(
        override val aggregateId: AggregateId,
        override val correlationId: String,
        override val updatedAt: Instant,
    ) : InboundBankTransactionEvent()

    /**
     * Bank transaction was created.
     */
    data class TransactionCreated(
        override val aggregateId: AggregateId,
        override val correlationId: String,
        override val updatedAt: Instant,
        val fromAccountId: AggregateId,
        val toAccountId: AggregateId,
        val amount: BigDecimal,
    ) : InboundBankTransactionEvent()

    /**
     * Money was successfully withdrawn from source account.
     */
    data class TransactionMoneyWithdrawn(
        override val aggregateId: AggregateId,
        override val correlationId: String,
        override val updatedAt: Instant,
        val accountId: AggregateId,
    ) : InboundBankTransactionEvent()

    /**
     * Money was successfully deposited to destination account.
     */
    data class TransactionMoneyDeposited(
        override val aggregateId: AggregateId,
        override val correlationId: String,
        override val updatedAt: Instant,
        val accountId: AggregateId,
    ) : InboundBankTransactionEvent()

    /**
     * Bank transaction completed successfully.
     */
    data class TransactionFinished(
        override val aggregateId: AggregateId,
        override val correlationId: String,
        override val updatedAt: Instant,
        val fromAccountId: AggregateId,
        val toAccountId: AggregateId,
    ) : InboundBankTransactionEvent()

    /**
     * Bank transaction failed and needs rollback.
     */
    data class TransactionFailed(
        override val aggregateId: AggregateId,
        override val correlationId: String,
        override val updatedAt: Instant,
        val fromAccountId: AggregateId,
        val toAccountId: AggregateId,
        val amount: BigDecimal,
        val reason: String,
    ) : InboundBankTransactionEvent()

    /**
     * Bank transaction was rolled back.
     */
    data class TransactionRolledBack(
        override val aggregateId: AggregateId,
        override val correlationId: String,
        override val updatedAt: Instant,
        val fromAccountId: AggregateId,
        val toAccountId: AggregateId,
        val amount: BigDecimal,
    ) : InboundBankTransactionEvent()

    /**
     * Withdraw phase was rolled back.
     */
    data class TransactionWithdrawRolledBack(
        override val aggregateId: AggregateId,
        override val correlationId: String,
        override val updatedAt: Instant,
        val fromAccountId: AggregateId,
        val amount: BigDecimal,
    ) : InboundBankTransactionEvent()

    /**
     * Deposit phase was rolled back.
     */
    data class TransactionDepositRolledBack(
        override val aggregateId: AggregateId,
        override val correlationId: String,
        override val updatedAt: Instant,
        val toAccountId: AggregateId,
        val amount: BigDecimal,
    ) : InboundBankTransactionEvent()
}
