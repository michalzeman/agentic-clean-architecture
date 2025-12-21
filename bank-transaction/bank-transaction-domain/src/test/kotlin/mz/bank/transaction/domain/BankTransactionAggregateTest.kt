package mz.bank.transaction.domain

import mz.shared.domain.AggregateId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

class BankTransactionAggregateTest {
    // ==================== Transaction Creation Tests ====================

    @Test
    fun `should create bankTransaction with valid parameters`() {
        // Given
        val cmd =
            BankTransactionCommand.CreateBankTransaction(
                correlationId = "corr-001",
                fromAccountId = AggregateId("acc-from"),
                toAccountId = AggregateId("acc-to"),
                amount = BigDecimal("100.00"),
            )

        // When
        val aggregate = BankTransactionAggregate.create(cmd)

        // Then
        assertThat(aggregate.bankTransaction.aggregateId.value).isNotBlank()
        assertThat(aggregate.bankTransaction.correlationId).isEqualTo("corr-001")
        assertThat(aggregate.bankTransaction.fromAccountId).isEqualTo(AggregateId("acc-from"))
        assertThat(aggregate.bankTransaction.toAccountId).isEqualTo(AggregateId("acc-to"))
        assertThat(aggregate.bankTransaction.amount).isEqualByComparingTo(BigDecimal("100.00"))
        assertThat(aggregate.bankTransaction.status).isEqualTo(BankTransactionStatus.CREATED)
        assertThat(aggregate.bankTransaction.moneyWithdrawn).isFalse()
        assertThat(aggregate.bankTransaction.moneyDeposited).isFalse()
        assertThat(aggregate.domainEvents).hasSize(1)
        assertThat(aggregate.domainEvents.first()).isInstanceOf(BankTransactionEvent.BankTransactionCreated::class.java)
    }

    @Test
    fun `should fail to create bankTransaction with negative amount`() {
        // Given
        val cmd =
            BankTransactionCommand.CreateBankTransaction(
                correlationId = "corr-002",
                fromAccountId = AggregateId("acc-from"),
                toAccountId = AggregateId("acc-to"),
                amount = BigDecimal("-50.00"),
            )

        // When & Then
        assertThatThrownBy { BankTransactionAggregate.create(cmd) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Transaction amount must be positive")
    }

    @Test
    fun `should fail to create bankTransaction with zero amount`() {
        // Given
        val cmd =
            BankTransactionCommand.CreateBankTransaction(
                correlationId = "corr-003",
                fromAccountId = AggregateId("acc-from"),
                toAccountId = AggregateId("acc-to"),
                amount = BigDecimal.ZERO,
            )

        // When & Then
        assertThatThrownBy { BankTransactionAggregate.create(cmd) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Transaction amount must be positive")
    }

    @Test
    fun `should fail to create bankTransaction with same source and destination accounts`() {
        // Given
        val cmd =
            BankTransactionCommand.CreateBankTransaction(
                correlationId = "corr-004",
                fromAccountId = AggregateId("acc-same"),
                toAccountId = AggregateId("acc-same"),
                amount = BigDecimal("100.00"),
            )

        // When & Then
        assertThatThrownBy { BankTransactionAggregate.create(cmd) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Source and destination accounts must be different")
    }

    @Test
    fun `should generate unique aggregate ID when creating transaction`() {
        // Given
        val cmd1 =
            BankTransactionCommand.CreateBankTransaction(
                correlationId = "corr-005",
                fromAccountId = AggregateId("acc-from-1"),
                toAccountId = AggregateId("acc-to-1"),
                amount = BigDecimal("100.00"),
            )
        val cmd2 =
            BankTransactionCommand.CreateBankTransaction(
                correlationId = "corr-006",
                fromAccountId = AggregateId("acc-from-2"),
                toAccountId = AggregateId("acc-to-2"),
                amount = BigDecimal("200.00"),
            )

        // When
        val aggregate1 = BankTransactionAggregate.create(cmd1)
        val aggregate2 = BankTransactionAggregate.create(cmd2)

        // Then
        assertThat(aggregate1.bankTransaction.aggregateId).isNotEqualTo(aggregate2.bankTransaction.aggregateId)
    }

    @Test
    fun `should include all details in BankTransactionCreated event`() {
        // Given
        val cmd =
            BankTransactionCommand.CreateBankTransaction(
                correlationId = "corr-007",
                fromAccountId = AggregateId("acc-from"),
                toAccountId = AggregateId("acc-to"),
                amount = BigDecimal("150.00"),
            )

        // When
        val aggregate = BankTransactionAggregate.create(cmd)

        // Then
        val event = aggregate.domainEvents.first() as BankTransactionEvent.BankTransactionCreated
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
            BankTransactionCommand.ValidateBankTransactionMoneyWithdraw(
                aggregateId = aggregate.bankTransaction.aggregateId,
                correlationId = aggregate.bankTransaction.correlationId,
            )

        // When
        val result = aggregate.validateMoneyWithdraw(cmd)

        // Then
        assertThat(result.bankTransaction.moneyWithdrawn).isTrue()
        assertThat(result.bankTransaction.moneyDeposited).isFalse()
        assertThat(result.bankTransaction.status).isEqualTo(BankTransactionStatus.CREATED)
        assertThat(result.domainEvents).hasSize(1)
        assertThat(result.domainEvents.first()).isInstanceOf(BankTransactionEvent.BankTransactionMoneyWithdrawn::class.java)
    }

    @Test
    fun `should fail to validate withdraw when bankTransaction is finished`() {
        // Given
        var aggregate = createTestAggregate()
        aggregate = aggregate.validateMoneyWithdraw(createWithdrawCommand(aggregate))
        aggregate = aggregate.validateMoneyDeposit(createDepositCommand(aggregate))
        aggregate = aggregate.finishBankTransaction(createFinishCommand(aggregate))

        val cmd =
            BankTransactionCommand.ValidateBankTransactionMoneyWithdraw(
                aggregateId = aggregate.bankTransaction.aggregateId,
                correlationId = aggregate.bankTransaction.correlationId,
            )

        // When & Then
        assertThatThrownBy { aggregate.validateMoneyWithdraw(cmd) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Cannot validate withdraw for bankTransaction in status")
    }

    @Test
    fun `should fail to validate withdraw when bankTransaction is failed`() {
        // Given
        var aggregate = createTestAggregate()
        aggregate = aggregate.cancelBankTransaction(createCancelCommand(aggregate))

        val cmd =
            BankTransactionCommand.ValidateBankTransactionMoneyWithdraw(
                aggregateId = aggregate.bankTransaction.aggregateId,
                correlationId = aggregate.bankTransaction.correlationId,
            )

        // When & Then
        assertThatThrownBy { aggregate.validateMoneyWithdraw(cmd) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Cannot validate withdraw for bankTransaction in status")
    }

    // ==================== Validate Money Deposit Tests ====================

    @Test
    fun `should validate money deposit successfully after withdraw`() {
        // Given
        var aggregate = createTestAggregate()
        aggregate = aggregate.validateMoneyWithdraw(createWithdrawCommand(aggregate))

        val cmd =
            BankTransactionCommand.ValidateBankTransactionMoneyDeposit(
                aggregateId = aggregate.bankTransaction.aggregateId,
                correlationId = aggregate.bankTransaction.correlationId,
            )

        // When
        val result = aggregate.validateMoneyDeposit(cmd)

        // Then
        assertThat(result.bankTransaction.moneyWithdrawn).isTrue()
        assertThat(result.bankTransaction.moneyDeposited).isTrue()
        assertThat(result.bankTransaction.status).isEqualTo(BankTransactionStatus.CREATED)
        assertThat(result.domainEvents).hasSize(1)
        assertThat(result.domainEvents.first()).isInstanceOf(BankTransactionEvent.BankTransactionMoneyDeposited::class.java)
    }

    @Test
    fun `should fail to validate deposit before withdraw`() {
        // Given
        val aggregate = createTestAggregate()
        val cmd =
            BankTransactionCommand.ValidateBankTransactionMoneyDeposit(
                aggregateId = aggregate.bankTransaction.aggregateId,
                correlationId = aggregate.bankTransaction.correlationId,
            )

        // When & Then
        assertThatThrownBy { aggregate.validateMoneyDeposit(cmd) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Cannot validate deposit before money is withdrawn")
    }

    @Test
    fun `should fail to validate deposit when bankTransaction is finished`() {
        // Given
        var aggregate = createTestAggregate()
        aggregate = aggregate.validateMoneyWithdraw(createWithdrawCommand(aggregate))
        aggregate = aggregate.validateMoneyDeposit(createDepositCommand(aggregate))
        aggregate = aggregate.finishBankTransaction(createFinishCommand(aggregate))

        val cmd =
            BankTransactionCommand.ValidateBankTransactionMoneyDeposit(
                aggregateId = aggregate.bankTransaction.aggregateId,
                correlationId = aggregate.bankTransaction.correlationId,
            )

        // When & Then
        assertThatThrownBy { aggregate.validateMoneyDeposit(cmd) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Cannot validate deposit for bankTransaction in status")
    }

    // ==================== Finish Transaction Tests ====================

    @Test
    fun `should finish bankTransaction successfully after withdraw and deposit`() {
        // Given
        var aggregate = createTestAggregate()
        aggregate = aggregate.validateMoneyWithdraw(createWithdrawCommand(aggregate))
        aggregate = aggregate.validateMoneyDeposit(createDepositCommand(aggregate))

        val cmd = createFinishCommand(aggregate)

        // When
        val result = aggregate.finishBankTransaction(cmd)

        // Then
        assertThat(result.bankTransaction.status).isEqualTo(BankTransactionStatus.FINISHED)
        assertThat(result.bankTransaction.moneyWithdrawn).isTrue()
        assertThat(result.bankTransaction.moneyDeposited).isTrue()
        assertThat(result.domainEvents).hasSize(1)
        assertThat(result.domainEvents.first()).isInstanceOf(BankTransactionEvent.BankTransactionFinished::class.java)
    }

    @Test
    fun `should fail to finish bankTransaction without withdraw completed`() {
        // Given
        val aggregate = createTestAggregate()
        val cmd = createFinishCommand(aggregate)

        // When & Then
        assertThatThrownBy { aggregate.finishBankTransaction(cmd) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("both withdraw and deposit must be completed")
    }

    @Test
    fun `should fail to finish bankTransaction without deposit completed`() {
        // Given
        var aggregate = createTestAggregate()
        aggregate = aggregate.validateMoneyWithdraw(createWithdrawCommand(aggregate))

        val cmd = createFinishCommand(aggregate)

        // When & Then
        assertThatThrownBy { aggregate.finishBankTransaction(cmd) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("both withdraw and deposit must be completed")
    }

    @Test
    fun `should fail to finish failed transaction`() {
        // Given
        var aggregate = createTestAggregate()
        aggregate = aggregate.cancelBankTransaction(createCancelCommand(aggregate))

        val cmd = createFinishCommand(aggregate)

        // When & Then
        assertThatThrownBy { aggregate.finishBankTransaction(cmd) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Cannot finish a failed transaction")
    }

    // ==================== Cancel Transaction Tests ====================

    @Test
    fun `should cancel bankTransaction before any operations`() {
        // Given
        val aggregate = createTestAggregate()
        val cmd = createCancelCommand(aggregate)

        // When
        val result = aggregate.cancelBankTransaction(cmd)

        // Then
        assertThat(result.bankTransaction.status).isEqualTo(BankTransactionStatus.FAILED)
        assertThat(result.bankTransaction.moneyWithdrawn).isFalse()
        assertThat(result.bankTransaction.moneyDeposited).isFalse()
        assertThat(result.domainEvents).hasSize(1)
        assertThat(result.domainEvents.first()).isInstanceOf(BankTransactionEvent.BankTransactionRolledBack::class.java)
    }

    @Test
    fun `should rollback withdraw when canceling after withdraw`() {
        // Given
        var aggregate = createTestAggregate()
        aggregate = aggregate.validateMoneyWithdraw(createWithdrawCommand(aggregate))

        val cmd = createCancelCommand(aggregate)

        // When
        val result = aggregate.cancelBankTransaction(cmd)

        // Then
        assertThat(result.bankTransaction.status).isEqualTo(BankTransactionStatus.FAILED)
        assertThat(result.bankTransaction.moneyWithdrawn).isFalse()
        assertThat(result.bankTransaction.moneyDeposited).isFalse()
        assertThat(result.domainEvents).hasSize(2)
        assertThat(result.domainEvents[0]).isInstanceOf(BankTransactionEvent.BankTransactionWithdrawRolledBack::class.java)
        assertThat(result.domainEvents[1]).isInstanceOf(BankTransactionEvent.BankTransactionRolledBack::class.java)
    }

    @Test
    fun `should rollback both withdraw and deposit when canceling after deposit`() {
        // Given
        var aggregate = createTestAggregate()
        aggregate = aggregate.validateMoneyWithdraw(createWithdrawCommand(aggregate))
        aggregate = aggregate.validateMoneyDeposit(createDepositCommand(aggregate))

        val cmd = createCancelCommand(aggregate)

        // When
        val result = aggregate.cancelBankTransaction(cmd)

        // Then
        assertThat(result.bankTransaction.status).isEqualTo(BankTransactionStatus.FAILED)
        assertThat(result.bankTransaction.moneyWithdrawn).isFalse()
        assertThat(result.bankTransaction.moneyDeposited).isFalse()
        assertThat(result.domainEvents).hasSize(3)
        assertThat(result.domainEvents[0]).isInstanceOf(BankTransactionEvent.BankTransactionDepositRolledBack::class.java)
        assertThat(result.domainEvents[1]).isInstanceOf(BankTransactionEvent.BankTransactionWithdrawRolledBack::class.java)
        assertThat(result.domainEvents[2]).isInstanceOf(BankTransactionEvent.BankTransactionRolledBack::class.java)
    }

    @Test
    fun `should fail to cancel finished transaction`() {
        // Given
        var aggregate = createTestAggregate()
        aggregate = aggregate.validateMoneyWithdraw(createWithdrawCommand(aggregate))
        aggregate = aggregate.validateMoneyDeposit(createDepositCommand(aggregate))
        aggregate = aggregate.finishBankTransaction(createFinishCommand(aggregate))

        val cmd = createCancelCommand(aggregate)

        // When & Then
        assertThatThrownBy { aggregate.cancelBankTransaction(cmd) }
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
                BankTransactionEvent.BankTransactionCreated(
                    aggregateId,
                    correlationId,
                    now,
                    AggregateId("acc-from"),
                    AggregateId("acc-to"),
                    BigDecimal("100.00"),
                ),
                BankTransactionEvent.BankTransactionMoneyWithdrawn(aggregateId, correlationId, now),
                BankTransactionEvent.BankTransactionMoneyDeposited(aggregateId, correlationId, now),
                BankTransactionEvent.BankTransactionFinished(
                    aggregateId,
                    correlationId,
                    now,
                    AggregateId("acc-from"),
                    AggregateId("acc-to"),
                ),
            )

        // When
        val aggregate = BankTransactionAggregate.fromEvents(aggregateId, correlationId, events)

        // Then
        assertThat(aggregate.bankTransaction.status).isEqualTo(BankTransactionStatus.FINISHED)
        assertThat(aggregate.bankTransaction.moneyWithdrawn).isTrue()
        assertThat(aggregate.bankTransaction.moneyDeposited).isTrue()
        assertThat(aggregate.bankTransaction.version).isEqualTo(4L)
        assertThat(aggregate.domainEvents).isEmpty()
    }

    @Test
    fun `should rebuild state from events - failed bankTransaction with rollback`() {
        // Given
        val aggregateId = AggregateId("txn-rebuild-002")
        val correlationId = "corr-rebuild-002"
        val now = Instant.now()
        val events =
            listOf(
                BankTransactionEvent.BankTransactionCreated(
                    aggregateId,
                    correlationId,
                    now,
                    AggregateId("acc-from"),
                    AggregateId("acc-to"),
                    BigDecimal("100.00"),
                ),
                BankTransactionEvent.BankTransactionMoneyWithdrawn(aggregateId, correlationId, now),
                BankTransactionEvent.BankTransactionWithdrawRolledBack(aggregateId, correlationId, now),
                BankTransactionEvent.BankTransactionRolledBack(
                    aggregateId,
                    correlationId,
                    now,
                    AggregateId("acc-from"),
                    AggregateId("acc-to"),
                    BigDecimal("100.00"),
                ),
            )

        // When
        val aggregate = BankTransactionAggregate.fromEvents(aggregateId, correlationId, events)

        // Then
        assertThat(aggregate.bankTransaction.status).isEqualTo(BankTransactionStatus.FAILED)
        assertThat(aggregate.bankTransaction.moneyWithdrawn).isFalse()
        assertThat(aggregate.bankTransaction.moneyDeposited).isFalse()
        assertThat(aggregate.bankTransaction.version).isEqualTo(4L)
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
                BankTransactionEvent.BankTransactionCreated(
                    aggregateId,
                    correlationId,
                    now,
                    AggregateId("acc-from"),
                    AggregateId("acc-to"),
                    BigDecimal("100.00"),
                ),
                BankTransactionEvent.BankTransactionMoneyWithdrawn(aggregateId, correlationId, now),
                BankTransactionEvent.BankTransactionMoneyDeposited(aggregateId, correlationId, now),
            )

        // When
        val aggregate = BankTransactionAggregate.fromEvents(aggregateId, correlationId, events)

        // Then
        assertThat(aggregate.bankTransaction.version).isEqualTo(3L)
    }

    @Test
    fun `should fail to rebuild from empty events list`() {
        // Given
        val aggregateId = AggregateId("txn-empty")
        val correlationId = "corr-empty"
        val events = emptyList<BankTransactionEvent>()

        // When & Then
        assertThatThrownBy { BankTransactionAggregate.fromEvents(aggregateId, correlationId, events) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Events list must not be empty")
    }

    // ==================== Transaction Lifecycle Tests ====================

    @Test
    fun `should execute complete successful bankTransaction lifecycle`() {
        // Given - Create transaction
        var aggregate = createTestAggregate()
        assertThat(aggregate.bankTransaction.status).isEqualTo(BankTransactionStatus.CREATED)
        assertThat(aggregate.bankTransaction.moneyWithdrawn).isFalse()
        assertThat(aggregate.bankTransaction.moneyDeposited).isFalse()

        // When - Validate withdraw
        aggregate = aggregate.validateMoneyWithdraw(createWithdrawCommand(aggregate))
        assertThat(aggregate.bankTransaction.status).isEqualTo(BankTransactionStatus.CREATED)
        assertThat(aggregate.bankTransaction.moneyWithdrawn).isTrue()
        assertThat(aggregate.bankTransaction.moneyDeposited).isFalse()

        // When - Validate deposit
        aggregate = aggregate.validateMoneyDeposit(createDepositCommand(aggregate))
        assertThat(aggregate.bankTransaction.status).isEqualTo(BankTransactionStatus.CREATED)
        assertThat(aggregate.bankTransaction.moneyWithdrawn).isTrue()
        assertThat(aggregate.bankTransaction.moneyDeposited).isTrue()

        // When - Finish transaction
        aggregate = aggregate.finishBankTransaction(createFinishCommand(aggregate))

        // Then - Transaction completed
        assertThat(aggregate.bankTransaction.status).isEqualTo(BankTransactionStatus.FINISHED)
        assertThat(aggregate.bankTransaction.moneyWithdrawn).isTrue()
        assertThat(aggregate.bankTransaction.moneyDeposited).isTrue()
    }

    @Test
    fun `should execute failed bankTransaction lifecycle with rollback`() {
        // Given - Create bankTransaction and withdraw
        var aggregate = createTestAggregate()
        aggregate = aggregate.validateMoneyWithdraw(createWithdrawCommand(aggregate))
        assertThat(aggregate.bankTransaction.status).isEqualTo(BankTransactionStatus.CREATED)
        assertThat(aggregate.bankTransaction.moneyWithdrawn).isTrue()

        // When - Cancel transaction
        aggregate = aggregate.cancelBankTransaction(createCancelCommand(aggregate))

        // Then - Transaction failed and rolled back
        assertThat(aggregate.bankTransaction.status).isEqualTo(BankTransactionStatus.FAILED)
        assertThat(aggregate.bankTransaction.moneyWithdrawn).isFalse()
        assertThat(aggregate.bankTransaction.moneyDeposited).isFalse()
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
        assertThat(aggregate.bankTransaction.moneyWithdrawn).isTrue()
        assertThat(aggregate.bankTransaction.moneyDeposited).isTrue()
        assertThat(aggregate.bankTransaction.status).isEqualTo(BankTransactionStatus.CREATED)
    }

    @Test
    fun `should maintain amount consistency throughout lifecycle`() {
        // Given
        val amount = BigDecimal("250.75")
        var aggregate =
            BankTransactionAggregate.create(
                BankTransactionCommand.CreateBankTransaction(
                    correlationId = "corr-amount",
                    fromAccountId = AggregateId("acc-from"),
                    toAccountId = AggregateId("acc-to"),
                    amount = amount,
                ),
            )

        // When - Execute full lifecycle
        aggregate = aggregate.validateMoneyWithdraw(createWithdrawCommand(aggregate))
        aggregate = aggregate.validateMoneyDeposit(createDepositCommand(aggregate))
        aggregate = aggregate.finishBankTransaction(createFinishCommand(aggregate))

        // Then - Amount remains unchanged
        assertThat(aggregate.bankTransaction.amount).isEqualByComparingTo(amount)
    }

    // ==================== Helper Methods ====================

    private fun createTestAggregate(): BankTransactionAggregate =
        BankTransactionAggregate.create(
            BankTransactionCommand.CreateBankTransaction(
                correlationId = "test-corr",
                fromAccountId = AggregateId("test-from"),
                toAccountId = AggregateId("test-to"),
                amount = BigDecimal("100.00"),
            ),
        )

    private fun createWithdrawCommand(aggregate: BankTransactionAggregate): BankTransactionCommand.ValidateBankTransactionMoneyWithdraw =
        BankTransactionCommand.ValidateBankTransactionMoneyWithdraw(
            aggregateId = aggregate.bankTransaction.aggregateId,
            correlationId = aggregate.bankTransaction.correlationId,
        )

    private fun createDepositCommand(aggregate: BankTransactionAggregate): BankTransactionCommand.ValidateBankTransactionMoneyDeposit =
        BankTransactionCommand.ValidateBankTransactionMoneyDeposit(
            aggregateId = aggregate.bankTransaction.aggregateId,
            correlationId = aggregate.bankTransaction.correlationId,
        )

    private fun createFinishCommand(aggregate: BankTransactionAggregate): BankTransactionCommand.FinishBankTransaction =
        BankTransactionCommand.FinishBankTransaction(
            aggregateId = aggregate.bankTransaction.aggregateId,
            correlationId = aggregate.bankTransaction.correlationId,
            fromAccountId = aggregate.bankTransaction.fromAccountId,
            toAccountId = aggregate.bankTransaction.toAccountId,
        )

    private fun createCancelCommand(aggregate: BankTransactionAggregate): BankTransactionCommand.CancelBankTransaction =
        BankTransactionCommand.CancelBankTransaction(
            aggregateId = aggregate.bankTransaction.aggregateId,
            correlationId = aggregate.bankTransaction.correlationId,
            fromAccountId = aggregate.bankTransaction.fromAccountId,
            toAccountId = aggregate.bankTransaction.toAccountId,
            amount = aggregate.bankTransaction.amount,
        )
}
