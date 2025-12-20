package mz.bank.transaction.domain

import mz.shared.domain.AggregateId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

class TransactionAggregateTest {
    // ==================== Transaction Creation Tests ====================

    @Test
    fun `should create transaction with valid parameters`() {
        // Given
        val cmd =
            TransactionCommand.CreateTransaction(
                correlationId = "corr-001",
                fromAccountId = AggregateId("acc-from"),
                toAccountId = AggregateId("acc-to"),
                amount = BigDecimal("100.00"),
            )

        // When
        val aggregate = TransactionAggregate.create(cmd)

        // Then
        assertThat(aggregate.transaction.aggregateId.value).isNotBlank()
        assertThat(aggregate.transaction.correlationId).isEqualTo("corr-001")
        assertThat(aggregate.transaction.fromAccountId).isEqualTo(AggregateId("acc-from"))
        assertThat(aggregate.transaction.toAccountId).isEqualTo(AggregateId("acc-to"))
        assertThat(aggregate.transaction.amount).isEqualByComparingTo(BigDecimal("100.00"))
        assertThat(aggregate.transaction.status).isEqualTo(TransactionStatus.CREATED)
        assertThat(aggregate.transaction.moneyWithdrawn).isFalse()
        assertThat(aggregate.transaction.moneyDeposited).isFalse()
        assertThat(aggregate.domainEvents).hasSize(1)
        assertThat(aggregate.domainEvents.first()).isInstanceOf(TransactionEvent.TransactionCreated::class.java)
    }

    @Test
    fun `should fail to create transaction with negative amount`() {
        // Given
        val cmd =
            TransactionCommand.CreateTransaction(
                correlationId = "corr-002",
                fromAccountId = AggregateId("acc-from"),
                toAccountId = AggregateId("acc-to"),
                amount = BigDecimal("-50.00"),
            )

        // When & Then
        assertThatThrownBy { TransactionAggregate.create(cmd) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Transaction amount must be positive")
    }

    @Test
    fun `should fail to create transaction with zero amount`() {
        // Given
        val cmd =
            TransactionCommand.CreateTransaction(
                correlationId = "corr-003",
                fromAccountId = AggregateId("acc-from"),
                toAccountId = AggregateId("acc-to"),
                amount = BigDecimal.ZERO,
            )

        // When & Then
        assertThatThrownBy { TransactionAggregate.create(cmd) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Transaction amount must be positive")
    }

    @Test
    fun `should fail to create transaction with same source and destination accounts`() {
        // Given
        val cmd =
            TransactionCommand.CreateTransaction(
                correlationId = "corr-004",
                fromAccountId = AggregateId("acc-same"),
                toAccountId = AggregateId("acc-same"),
                amount = BigDecimal("100.00"),
            )

        // When & Then
        assertThatThrownBy { TransactionAggregate.create(cmd) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Source and destination accounts must be different")
    }

    @Test
    fun `should generate unique aggregate ID when creating transaction`() {
        // Given
        val cmd1 =
            TransactionCommand.CreateTransaction(
                correlationId = "corr-005",
                fromAccountId = AggregateId("acc-from-1"),
                toAccountId = AggregateId("acc-to-1"),
                amount = BigDecimal("100.00"),
            )
        val cmd2 =
            TransactionCommand.CreateTransaction(
                correlationId = "corr-006",
                fromAccountId = AggregateId("acc-from-2"),
                toAccountId = AggregateId("acc-to-2"),
                amount = BigDecimal("200.00"),
            )

        // When
        val aggregate1 = TransactionAggregate.create(cmd1)
        val aggregate2 = TransactionAggregate.create(cmd2)

        // Then
        assertThat(aggregate1.transaction.aggregateId).isNotEqualTo(aggregate2.transaction.aggregateId)
    }

    @Test
    fun `should include all details in TransactionCreated event`() {
        // Given
        val cmd =
            TransactionCommand.CreateTransaction(
                correlationId = "corr-007",
                fromAccountId = AggregateId("acc-from"),
                toAccountId = AggregateId("acc-to"),
                amount = BigDecimal("150.00"),
            )

        // When
        val aggregate = TransactionAggregate.create(cmd)

        // Then
        val event = aggregate.domainEvents.first() as TransactionEvent.TransactionCreated
        assertThat(event.correlationId).isEqualTo("corr-007")
        assertThat(event.fromAccountId).isEqualTo(AggregateId("acc-from"))
        assertThat(event.toAccountId).isEqualTo(AggregateId("acc-to"))
        assertThat(event.amount).isEqualByComparingTo(BigDecimal("150.00"))
    }

    // ==================== Validate Money Withdraw Tests ====================

    @Test
    fun `should validate money withdraw successfully`() {
        // Given
        val aggregate = createTestAggregate()
        val cmd =
            TransactionCommand.ValidateTransactionMoneyWithdraw(
                aggregateId = aggregate.transaction.aggregateId,
                correlationId = aggregate.transaction.correlationId,
            )

        // When
        val result = aggregate.validateMoneyWithdraw(cmd)

        // Then
        assertThat(result.transaction.moneyWithdrawn).isTrue()
        assertThat(result.transaction.moneyDeposited).isFalse()
        assertThat(result.transaction.status).isEqualTo(TransactionStatus.CREATED)
        assertThat(result.domainEvents).hasSize(1)
        assertThat(result.domainEvents.first()).isInstanceOf(TransactionEvent.TransactionMoneyWithdrawn::class.java)
    }

    @Test
    fun `should fail to validate withdraw when transaction is finished`() {
        // Given
        var aggregate = createTestAggregate()
        aggregate = aggregate.validateMoneyWithdraw(createWithdrawCommand(aggregate))
        aggregate = aggregate.validateMoneyDeposit(createDepositCommand(aggregate))
        aggregate = aggregate.finishTransaction(createFinishCommand(aggregate))

        val cmd =
            TransactionCommand.ValidateTransactionMoneyWithdraw(
                aggregateId = aggregate.transaction.aggregateId,
                correlationId = aggregate.transaction.correlationId,
            )

        // When & Then
        assertThatThrownBy { aggregate.validateMoneyWithdraw(cmd) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Cannot validate withdraw for transaction in status")
    }

    @Test
    fun `should fail to validate withdraw when transaction is failed`() {
        // Given
        var aggregate = createTestAggregate()
        aggregate = aggregate.cancelTransaction(createCancelCommand(aggregate))

        val cmd =
            TransactionCommand.ValidateTransactionMoneyWithdraw(
                aggregateId = aggregate.transaction.aggregateId,
                correlationId = aggregate.transaction.correlationId,
            )

        // When & Then
        assertThatThrownBy { aggregate.validateMoneyWithdraw(cmd) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Cannot validate withdraw for transaction in status")
    }

    // ==================== Validate Money Deposit Tests ====================

    @Test
    fun `should validate money deposit successfully after withdraw`() {
        // Given
        var aggregate = createTestAggregate()
        aggregate = aggregate.validateMoneyWithdraw(createWithdrawCommand(aggregate))

        val cmd =
            TransactionCommand.ValidateTransactionMoneyDeposit(
                aggregateId = aggregate.transaction.aggregateId,
                correlationId = aggregate.transaction.correlationId,
            )

        // When
        val result = aggregate.validateMoneyDeposit(cmd)

        // Then
        assertThat(result.transaction.moneyWithdrawn).isTrue()
        assertThat(result.transaction.moneyDeposited).isTrue()
        assertThat(result.transaction.status).isEqualTo(TransactionStatus.CREATED)
        assertThat(result.domainEvents).hasSize(1)
        assertThat(result.domainEvents.first()).isInstanceOf(TransactionEvent.TransactionMoneyDeposited::class.java)
    }

    @Test
    fun `should fail to validate deposit before withdraw`() {
        // Given
        val aggregate = createTestAggregate()
        val cmd =
            TransactionCommand.ValidateTransactionMoneyDeposit(
                aggregateId = aggregate.transaction.aggregateId,
                correlationId = aggregate.transaction.correlationId,
            )

        // When & Then
        assertThatThrownBy { aggregate.validateMoneyDeposit(cmd) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Cannot validate deposit before money is withdrawn")
    }

    @Test
    fun `should fail to validate deposit when transaction is finished`() {
        // Given
        var aggregate = createTestAggregate()
        aggregate = aggregate.validateMoneyWithdraw(createWithdrawCommand(aggregate))
        aggregate = aggregate.validateMoneyDeposit(createDepositCommand(aggregate))
        aggregate = aggregate.finishTransaction(createFinishCommand(aggregate))

        val cmd =
            TransactionCommand.ValidateTransactionMoneyDeposit(
                aggregateId = aggregate.transaction.aggregateId,
                correlationId = aggregate.transaction.correlationId,
            )

        // When & Then
        assertThatThrownBy { aggregate.validateMoneyDeposit(cmd) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Cannot validate deposit for transaction in status")
    }

    // ==================== Finish Transaction Tests ====================

    @Test
    fun `should finish transaction successfully after withdraw and deposit`() {
        // Given
        var aggregate = createTestAggregate()
        aggregate = aggregate.validateMoneyWithdraw(createWithdrawCommand(aggregate))
        aggregate = aggregate.validateMoneyDeposit(createDepositCommand(aggregate))

        val cmd = createFinishCommand(aggregate)

        // When
        val result = aggregate.finishTransaction(cmd)

        // Then
        assertThat(result.transaction.status).isEqualTo(TransactionStatus.FINISHED)
        assertThat(result.transaction.moneyWithdrawn).isTrue()
        assertThat(result.transaction.moneyDeposited).isTrue()
        assertThat(result.domainEvents).hasSize(1)
        assertThat(result.domainEvents.first()).isInstanceOf(TransactionEvent.TransactionFinished::class.java)
    }

    @Test
    fun `should fail to finish transaction without withdraw completed`() {
        // Given
        val aggregate = createTestAggregate()
        val cmd = createFinishCommand(aggregate)

        // When & Then
        assertThatThrownBy { aggregate.finishTransaction(cmd) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("both withdraw and deposit must be completed")
    }

    @Test
    fun `should fail to finish transaction without deposit completed`() {
        // Given
        var aggregate = createTestAggregate()
        aggregate = aggregate.validateMoneyWithdraw(createWithdrawCommand(aggregate))

        val cmd = createFinishCommand(aggregate)

        // When & Then
        assertThatThrownBy { aggregate.finishTransaction(cmd) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("both withdraw and deposit must be completed")
    }

    @Test
    fun `should fail to finish failed transaction`() {
        // Given
        var aggregate = createTestAggregate()
        aggregate = aggregate.cancelTransaction(createCancelCommand(aggregate))

        val cmd = createFinishCommand(aggregate)

        // When & Then
        assertThatThrownBy { aggregate.finishTransaction(cmd) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Cannot finish a failed transaction")
    }

    // ==================== Cancel Transaction Tests ====================

    @Test
    fun `should cancel transaction before any operations`() {
        // Given
        val aggregate = createTestAggregate()
        val cmd = createCancelCommand(aggregate)

        // When
        val result = aggregate.cancelTransaction(cmd)

        // Then
        assertThat(result.transaction.status).isEqualTo(TransactionStatus.FAILED)
        assertThat(result.transaction.moneyWithdrawn).isFalse()
        assertThat(result.transaction.moneyDeposited).isFalse()
        assertThat(result.domainEvents).hasSize(1)
        assertThat(result.domainEvents.first()).isInstanceOf(TransactionEvent.TransactionRolledBack::class.java)
    }

    @Test
    fun `should rollback withdraw when canceling after withdraw`() {
        // Given
        var aggregate = createTestAggregate()
        aggregate = aggregate.validateMoneyWithdraw(createWithdrawCommand(aggregate))

        val cmd = createCancelCommand(aggregate)

        // When
        val result = aggregate.cancelTransaction(cmd)

        // Then
        assertThat(result.transaction.status).isEqualTo(TransactionStatus.FAILED)
        assertThat(result.transaction.moneyWithdrawn).isFalse()
        assertThat(result.transaction.moneyDeposited).isFalse()
        assertThat(result.domainEvents).hasSize(2)
        assertThat(result.domainEvents[0]).isInstanceOf(TransactionEvent.TransactionWithdrawRolledBack::class.java)
        assertThat(result.domainEvents[1]).isInstanceOf(TransactionEvent.TransactionRolledBack::class.java)
    }

    @Test
    fun `should rollback both withdraw and deposit when canceling after deposit`() {
        // Given
        var aggregate = createTestAggregate()
        aggregate = aggregate.validateMoneyWithdraw(createWithdrawCommand(aggregate))
        aggregate = aggregate.validateMoneyDeposit(createDepositCommand(aggregate))

        val cmd = createCancelCommand(aggregate)

        // When
        val result = aggregate.cancelTransaction(cmd)

        // Then
        assertThat(result.transaction.status).isEqualTo(TransactionStatus.FAILED)
        assertThat(result.transaction.moneyWithdrawn).isFalse()
        assertThat(result.transaction.moneyDeposited).isFalse()
        assertThat(result.domainEvents).hasSize(3)
        assertThat(result.domainEvents[0]).isInstanceOf(TransactionEvent.TransactionDepositRolledBack::class.java)
        assertThat(result.domainEvents[1]).isInstanceOf(TransactionEvent.TransactionWithdrawRolledBack::class.java)
        assertThat(result.domainEvents[2]).isInstanceOf(TransactionEvent.TransactionRolledBack::class.java)
    }

    @Test
    fun `should fail to cancel finished transaction`() {
        // Given
        var aggregate = createTestAggregate()
        aggregate = aggregate.validateMoneyWithdraw(createWithdrawCommand(aggregate))
        aggregate = aggregate.validateMoneyDeposit(createDepositCommand(aggregate))
        aggregate = aggregate.finishTransaction(createFinishCommand(aggregate))

        val cmd = createCancelCommand(aggregate)

        // When & Then
        assertThatThrownBy { aggregate.cancelTransaction(cmd) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Cannot cancel a finished transaction")
    }

    // ==================== Event Sourcing Tests ====================

    @Test
    fun `should rebuild state from events - successful transaction`() {
        // Given
        val aggregateId = AggregateId("txn-rebuild-001")
        val correlationId = "corr-rebuild-001"
        val now = Instant.now()
        val events =
            listOf(
                TransactionEvent.TransactionCreated(
                    aggregateId,
                    correlationId,
                    now,
                    AggregateId("acc-from"),
                    AggregateId("acc-to"),
                    BigDecimal("100.00"),
                ),
                TransactionEvent.TransactionMoneyWithdrawn(aggregateId, correlationId, now),
                TransactionEvent.TransactionMoneyDeposited(aggregateId, correlationId, now),
                TransactionEvent.TransactionFinished(aggregateId, correlationId, now, AggregateId("acc-from"), AggregateId("acc-to")),
            )

        // When
        val aggregate = TransactionAggregate.fromEvents(aggregateId, correlationId, events)

        // Then
        assertThat(aggregate.transaction.status).isEqualTo(TransactionStatus.FINISHED)
        assertThat(aggregate.transaction.moneyWithdrawn).isTrue()
        assertThat(aggregate.transaction.moneyDeposited).isTrue()
        assertThat(aggregate.transaction.version).isEqualTo(4L)
        assertThat(aggregate.domainEvents).isEmpty()
    }

    @Test
    fun `should rebuild state from events - failed transaction with rollback`() {
        // Given
        val aggregateId = AggregateId("txn-rebuild-002")
        val correlationId = "corr-rebuild-002"
        val now = Instant.now()
        val events =
            listOf(
                TransactionEvent.TransactionCreated(
                    aggregateId,
                    correlationId,
                    now,
                    AggregateId("acc-from"),
                    AggregateId("acc-to"),
                    BigDecimal("100.00"),
                ),
                TransactionEvent.TransactionMoneyWithdrawn(aggregateId, correlationId, now),
                TransactionEvent.TransactionWithdrawRolledBack(aggregateId, correlationId, now),
                TransactionEvent.TransactionRolledBack(
                    aggregateId,
                    correlationId,
                    now,
                    AggregateId("acc-from"),
                    AggregateId("acc-to"),
                    BigDecimal("100.00"),
                ),
            )

        // When
        val aggregate = TransactionAggregate.fromEvents(aggregateId, correlationId, events)

        // Then
        assertThat(aggregate.transaction.status).isEqualTo(TransactionStatus.FAILED)
        assertThat(aggregate.transaction.moneyWithdrawn).isFalse()
        assertThat(aggregate.transaction.moneyDeposited).isFalse()
        assertThat(aggregate.transaction.version).isEqualTo(4L)
        assertThat(aggregate.domainEvents).isEmpty()
    }

    @Test
    fun `should track version increments on each event`() {
        // Given
        val aggregateId = AggregateId("txn-version")
        val correlationId = "corr-version"
        val now = Instant.now()
        val events =
            listOf(
                TransactionEvent.TransactionCreated(
                    aggregateId,
                    correlationId,
                    now,
                    AggregateId("acc-from"),
                    AggregateId("acc-to"),
                    BigDecimal("100.00"),
                ),
                TransactionEvent.TransactionMoneyWithdrawn(aggregateId, correlationId, now),
                TransactionEvent.TransactionMoneyDeposited(aggregateId, correlationId, now),
            )

        // When
        val aggregate = TransactionAggregate.fromEvents(aggregateId, correlationId, events)

        // Then
        assertThat(aggregate.transaction.version).isEqualTo(3L)
    }

    @Test
    fun `should fail to rebuild from empty events list`() {
        // Given
        val aggregateId = AggregateId("txn-empty")
        val correlationId = "corr-empty"
        val events = emptyList<TransactionEvent>()

        // When & Then
        assertThatThrownBy { TransactionAggregate.fromEvents(aggregateId, correlationId, events) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Events list must not be empty")
    }

    // ==================== Transaction Lifecycle Tests ====================

    @Test
    fun `should execute complete successful transaction lifecycle`() {
        // Given - Create transaction
        var aggregate = createTestAggregate()
        assertThat(aggregate.transaction.status).isEqualTo(TransactionStatus.CREATED)
        assertThat(aggregate.transaction.moneyWithdrawn).isFalse()
        assertThat(aggregate.transaction.moneyDeposited).isFalse()

        // When - Validate withdraw
        aggregate = aggregate.validateMoneyWithdraw(createWithdrawCommand(aggregate))
        assertThat(aggregate.transaction.status).isEqualTo(TransactionStatus.CREATED)
        assertThat(aggregate.transaction.moneyWithdrawn).isTrue()
        assertThat(aggregate.transaction.moneyDeposited).isFalse()

        // When - Validate deposit
        aggregate = aggregate.validateMoneyDeposit(createDepositCommand(aggregate))
        assertThat(aggregate.transaction.status).isEqualTo(TransactionStatus.CREATED)
        assertThat(aggregate.transaction.moneyWithdrawn).isTrue()
        assertThat(aggregate.transaction.moneyDeposited).isTrue()

        // When - Finish transaction
        aggregate = aggregate.finishTransaction(createFinishCommand(aggregate))

        // Then - Transaction completed
        assertThat(aggregate.transaction.status).isEqualTo(TransactionStatus.FINISHED)
        assertThat(aggregate.transaction.moneyWithdrawn).isTrue()
        assertThat(aggregate.transaction.moneyDeposited).isTrue()
    }

    @Test
    fun `should execute failed transaction lifecycle with rollback`() {
        // Given - Create transaction and withdraw
        var aggregate = createTestAggregate()
        aggregate = aggregate.validateMoneyWithdraw(createWithdrawCommand(aggregate))
        assertThat(aggregate.transaction.status).isEqualTo(TransactionStatus.CREATED)
        assertThat(aggregate.transaction.moneyWithdrawn).isTrue()

        // When - Cancel transaction
        aggregate = aggregate.cancelTransaction(createCancelCommand(aggregate))

        // Then - Transaction failed and rolled back
        assertThat(aggregate.transaction.status).isEqualTo(TransactionStatus.FAILED)
        assertThat(aggregate.transaction.moneyWithdrawn).isFalse()
        assertThat(aggregate.transaction.moneyDeposited).isFalse()
    }

    // ==================== Invariant Tests ====================

    @Test
    fun `should maintain consistent state through event application`() {
        // Given
        var aggregate = createTestAggregate()

        // When - Apply various events
        aggregate = aggregate.validateMoneyWithdraw(createWithdrawCommand(aggregate))
        aggregate = aggregate.validateMoneyDeposit(createDepositCommand(aggregate))

        // Then - State is consistent
        assertThat(aggregate.transaction.moneyWithdrawn).isTrue()
        assertThat(aggregate.transaction.moneyDeposited).isTrue()
        assertThat(aggregate.transaction.status).isEqualTo(TransactionStatus.CREATED)
    }

    @Test
    fun `should maintain amount consistency throughout lifecycle`() {
        // Given
        val amount = BigDecimal("250.75")
        var aggregate =
            TransactionAggregate.create(
                TransactionCommand.CreateTransaction(
                    correlationId = "corr-amount",
                    fromAccountId = AggregateId("acc-from"),
                    toAccountId = AggregateId("acc-to"),
                    amount = amount,
                ),
            )

        // When - Execute full lifecycle
        aggregate = aggregate.validateMoneyWithdraw(createWithdrawCommand(aggregate))
        aggregate = aggregate.validateMoneyDeposit(createDepositCommand(aggregate))
        aggregate = aggregate.finishTransaction(createFinishCommand(aggregate))

        // Then - Amount remains unchanged
        assertThat(aggregate.transaction.amount).isEqualByComparingTo(amount)
    }

    // ==================== Helper Methods ====================

    private fun createTestAggregate(): TransactionAggregate =
        TransactionAggregate.create(
            TransactionCommand.CreateTransaction(
                correlationId = "test-corr",
                fromAccountId = AggregateId("test-from"),
                toAccountId = AggregateId("test-to"),
                amount = BigDecimal("100.00"),
            ),
        )

    private fun createWithdrawCommand(aggregate: TransactionAggregate): TransactionCommand.ValidateTransactionMoneyWithdraw =
        TransactionCommand.ValidateTransactionMoneyWithdraw(
            aggregateId = aggregate.transaction.aggregateId,
            correlationId = aggregate.transaction.correlationId,
        )

    private fun createDepositCommand(aggregate: TransactionAggregate): TransactionCommand.ValidateTransactionMoneyDeposit =
        TransactionCommand.ValidateTransactionMoneyDeposit(
            aggregateId = aggregate.transaction.aggregateId,
            correlationId = aggregate.transaction.correlationId,
        )

    private fun createFinishCommand(aggregate: TransactionAggregate): TransactionCommand.FinishTransaction =
        TransactionCommand.FinishTransaction(
            aggregateId = aggregate.transaction.aggregateId,
            correlationId = aggregate.transaction.correlationId,
            fromAccountId = aggregate.transaction.fromAccountId,
            toAccountId = aggregate.transaction.toAccountId,
        )

    private fun createCancelCommand(aggregate: TransactionAggregate): TransactionCommand.CancelTransaction =
        TransactionCommand.CancelTransaction(
            aggregateId = aggregate.transaction.aggregateId,
            correlationId = aggregate.transaction.correlationId,
            fromAccountId = aggregate.transaction.fromAccountId,
            toAccountId = aggregate.transaction.toAccountId,
            amount = aggregate.transaction.amount,
        )
}
