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
    fun `should create bankTransaction with valid parameters and emit correct event`() {
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

        // Then - verify aggregate state
        assertThat(aggregate.bankTransaction.aggregateId.value).isNotBlank()
        assertThat(aggregate.bankTransaction.correlationId).isEqualTo("corr-001")
        assertThat(aggregate.bankTransaction.fromAccountId).isEqualTo(AggregateId("acc-from"))
        assertThat(aggregate.bankTransaction.toAccountId).isEqualTo(AggregateId("acc-to"))
        assertThat(aggregate.bankTransaction.amount).isEqualByComparingTo(BigDecimal("100.00"))
        assertThat(aggregate.bankTransaction.status).isEqualTo(BankTransactionStatus.CREATED)
        assertThat(aggregate.bankTransaction.moneyWithdrawn).isFalse()
        assertThat(aggregate.bankTransaction.moneyDeposited).isFalse()

        // Then - verify event
        assertThat(aggregate.domainEvents).hasSize(1)
        val event = aggregate.domainEvents.first() as BankTransactionEvent.BankTransactionCreated
        assertThat(event.correlationId).isEqualTo("corr-001")
        assertThat(event.amount).isEqualByComparingTo(BigDecimal("100.00"))
    }

    // ==================== Validate Money Withdraw Tests ====================

    @Test
    fun `should validate money withdraw successfully`() {
        // Given
        val aggregate = createTestAggregate()
        val cmd =
            BankTransactionCommand.ValidateBankTransactionMoneyWithdraw(
                aggregateId = aggregate.bankTransaction.aggregateId,
                accountId = aggregate.bankTransaction.fromAccountId,
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
    fun `should acknowledge or compensate late withdraw confirmation on terminal states`() {
        // FINISHED — ack with empty events, state unchanged
        var aggregate = createTestAggregate()
        aggregate = aggregate.validateMoneyWithdraw(createWithdrawCommand(aggregate))
        aggregate = aggregate.validateMoneyDeposit(createDepositCommand(aggregate))
        assertThat(aggregate.bankTransaction.status).isEqualTo(BankTransactionStatus.FINISHED)

        val finishedResult = aggregate.validateMoneyWithdraw(createWithdrawCommand(aggregate))
        assertThat(finishedResult.domainEvents).isEmpty()
        assertThat(finishedResult.bankTransaction.status).isEqualTo(BankTransactionStatus.FINISHED)
        assertThat(finishedResult.bankTransaction.moneyWithdrawn).isTrue()

        // FAILED — emit compensation event, set withdrawCompensated flag
        aggregate = createTestAggregate()
        aggregate = aggregate.cancelBankTransaction(createCancelCommand(aggregate))

        val failedResult = aggregate.validateMoneyWithdraw(createWithdrawCommand(aggregate))
        assertThat(failedResult.bankTransaction.status).isEqualTo(BankTransactionStatus.FAILED)
        assertThat(failedResult.bankTransaction.withdrawCompensated).isTrue()
        assertThat(failedResult.domainEvents).hasSize(1)
        assertThat(failedResult.domainEvents.first())
            .isInstanceOf(BankTransactionEvent.BankTransactionWithdrawRolledBack::class.java)
    }

    @Test
    fun `should fail to validate withdraw when accountId does not match fromAccountId`() {
        // Given
        val aggregate = createTestAggregate()
        val cmd =
            BankTransactionCommand.ValidateBankTransactionMoneyWithdraw(
                aggregateId = aggregate.bankTransaction.aggregateId,
                accountId = AggregateId("wrong-account-id"),
                correlationId = aggregate.bankTransaction.correlationId,
            )

        // When & Then
        assertThatThrownBy { aggregate.validateMoneyWithdraw(cmd) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("AccountId")
            .hasMessageContaining("does not match transaction's fromAccountId")
    }

    // ==================== Validate Money Deposit Tests ====================

    @Test
    fun `should validate money deposit and auto-finish when both operations complete`() {
        // Test deposit after withdraw - auto-finishes
        var aggregate = createTestAggregate()
        aggregate = aggregate.validateMoneyWithdraw(createWithdrawCommand(aggregate))

        val result = aggregate.validateMoneyDeposit(createDepositCommand(aggregate))

        assertThat(result.bankTransaction.moneyWithdrawn).isTrue()
        assertThat(result.bankTransaction.moneyDeposited).isTrue()
        assertThat(result.bankTransaction.status).isEqualTo(BankTransactionStatus.FINISHED)
        assertThat(result.domainEvents).hasSize(1)
        assertThat(result.domainEvents.first()).isInstanceOf(BankTransactionEvent.BankTransactionFinished::class.java)

        // Test deposit before withdraw - does not finish
        aggregate = createTestAggregate()
        val result2 = aggregate.validateMoneyDeposit(createDepositCommand(aggregate))

        assertThat(result2.bankTransaction.moneyDeposited).isTrue()
        assertThat(result2.bankTransaction.moneyWithdrawn).isFalse()
        assertThat(result2.bankTransaction.status).isEqualTo(BankTransactionStatus.CREATED)
        assertThat(result2.domainEvents).hasSize(1)
        assertThat(result2.domainEvents.first()).isInstanceOf(BankTransactionEvent.BankTransactionMoneyDeposited::class.java)
    }

    @Test
    fun `should acknowledge late deposit confirmation on finished transaction`() {
        // Given - Create, validate withdraw, then validate deposit (which auto-finishes)
        var aggregate = createTestAggregate()
        aggregate = aggregate.validateMoneyWithdraw(createWithdrawCommand(aggregate))
        aggregate = aggregate.validateMoneyDeposit(createDepositCommand(aggregate))
        assertThat(aggregate.bankTransaction.status).isEqualTo(BankTransactionStatus.FINISHED)

        // When - late deposit confirmation arrives
        val result = aggregate.validateMoneyDeposit(createDepositCommand(aggregate))

        // Then - ack with empty events, state unchanged
        assertThat(result.domainEvents).isEmpty()
        assertThat(result.bankTransaction.status).isEqualTo(BankTransactionStatus.FINISHED)
        assertThat(result.bankTransaction.moneyDeposited).isTrue()
    }

    @Test
    fun `should fail to validate deposit when accountId does not match toAccountId`() {
        // Given
        val aggregate = createTestAggregate()
        val cmd =
            BankTransactionCommand.ValidateBankTransactionMoneyDeposit(
                aggregateId = aggregate.bankTransaction.aggregateId,
                accountId = AggregateId("wrong-account-id"),
                correlationId = aggregate.bankTransaction.correlationId,
            )

        // When & Then
        assertThatThrownBy { aggregate.validateMoneyDeposit(cmd) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("AccountId")
            .hasMessageContaining("does not match transaction's toAccountId")
    }

    // ==================== Finish Transaction Tests ====================

    @Test
    fun `should return same aggregate when finish is called on already finished transaction`() {
        // Given - Create a transaction that is already FINISHED
        var aggregate = createTestAggregate()
        aggregate = aggregate.validateMoneyWithdraw(createWithdrawCommand(aggregate))
        aggregate = aggregate.validateMoneyDeposit(createDepositCommand(aggregate))
        assertThat(aggregate.bankTransaction.status).isEqualTo(BankTransactionStatus.FINISHED)

        val cmd = createFinishCommand(aggregate)

        // When - Call finishBankTransaction again (idempotent)
        val result = aggregate.finishBankTransaction(cmd)

        // Then - Should return the same aggregate with no new events emitted
        assertThat(result).isSameAs(aggregate)
        assertThat(result.bankTransaction.status).isEqualTo(BankTransactionStatus.FINISHED)
        assertThat(result.domainEvents).isEqualTo(aggregate.domainEvents)
    }

    // ==================== Cancel Transaction Tests ====================

    @Test
    fun `should cancel bankTransaction and rollback operations`() {
        // Test cancel before any operations
        var aggregate = createTestAggregate()
        var result = aggregate.cancelBankTransaction(createCancelCommand(aggregate))

        assertThat(result.bankTransaction.status).isEqualTo(BankTransactionStatus.FAILED)
        assertThat(result.bankTransaction.moneyWithdrawn).isFalse()
        assertThat(result.bankTransaction.moneyDeposited).isFalse()
        assertThat(result.domainEvents).hasSize(1)
        assertThat(result.domainEvents.first()).isInstanceOf(BankTransactionEvent.BankTransactionRolledBack::class.java)

        // Test rollback withdraw when canceling after withdraw
        aggregate = createTestAggregate()
        aggregate = aggregate.validateMoneyWithdraw(createWithdrawCommand(aggregate))
        result = aggregate.cancelBankTransaction(createCancelCommand(aggregate))

        assertThat(result.bankTransaction.status).isEqualTo(BankTransactionStatus.FAILED)
        assertThat(result.bankTransaction.moneyWithdrawn).isFalse()
        assertThat(result.bankTransaction.moneyDeposited).isFalse()
        assertThat(result.domainEvents).hasSize(2)
        assertThat(result.domainEvents[0]).isInstanceOf(BankTransactionEvent.BankTransactionWithdrawRolledBack::class.java)
        assertThat(result.domainEvents[1]).isInstanceOf(BankTransactionEvent.BankTransactionRolledBack::class.java)
    }

    @Test
    fun `should fail to cancel finished transaction`() {
        // Given
        var aggregate = createTestAggregate()
        aggregate = aggregate.validateMoneyWithdraw(createWithdrawCommand(aggregate))
        aggregate = aggregate.validateMoneyDeposit(createDepositCommand(aggregate))
        // Transaction is already finished after both validations

        val cmd = createCancelCommand(aggregate)

        // When & Then
        assertThatThrownBy { aggregate.cancelBankTransaction(cmd) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Cannot cancel a finished transaction")
    }

    @Test
    fun `should set rollback reason to MANUAL_CANCEL when cancellation command carries MANUAL_CANCEL`() {
        // Given
        val aggregate = createTestAggregate()
        val cmd = createCancelCommand(aggregate).copy(reason = TransactionRollbackReason.MANUAL_CANCEL)

        // When
        val result = aggregate.cancelBankTransaction(cmd)

        // Then
        assertThat(result.bankTransaction.rollbackReason).isEqualTo(TransactionRollbackReason.MANUAL_CANCEL)
    }

    @Test
    fun `should default rollback reason to UNKNOWN when cancellation command omits reason`() {
        // Given
        val aggregate = createTestAggregate()
        val cmd = createCancelCommand(aggregate)

        // When
        val result = aggregate.cancelBankTransaction(cmd)

        // Then
        assertThat(result.bankTransaction.rollbackReason).isEqualTo(TransactionRollbackReason.UNKNOWN)
    }

    @Test
    fun `should set rollback reason on BankTransactionRolledBack event`() {
        // Given
        val aggregate = createTestAggregate()
        val cmd = createCancelCommand(aggregate).copy(reason = TransactionRollbackReason.MANUAL_CANCEL)

        // When
        val result = aggregate.cancelBankTransaction(cmd)

        // Then
        val rolledBackEvent = result.domainEvents.filterIsInstance<BankTransactionEvent.BankTransactionRolledBack>().first()
        assertThat(rolledBackEvent.reason).isEqualTo(TransactionRollbackReason.MANUAL_CANCEL)
    }

    // ==================== Event Sourcing Tests ====================

    @Test
    fun `should rebuild state from events`() {
        // Test successful transaction rebuild
        val aggregateId1 = AggregateId("txn-rebuild-001")
        val correlationId1 = "corr-rebuild-001"
        val now = Instant.now()
        val successEvents =
            listOf(
                BankTransactionEvent.BankTransactionCreated(
                    aggregateId1,
                    correlationId1,
                    now,
                    AggregateId("acc-from"),
                    AggregateId("acc-to"),
                    BigDecimal("100.00"),
                ),
                BankTransactionEvent.BankTransactionMoneyWithdrawn(
                    aggregateId1,
                    correlationId1,
                    now,
                    AggregateId("acc-from"),
                    AggregateId("acc-to"),
                    BigDecimal("100.00"),
                ),
                BankTransactionEvent.BankTransactionMoneyDeposited(
                    aggregateId1,
                    correlationId1,
                    now,
                    AggregateId("acc-to"),
                ),
                BankTransactionEvent.BankTransactionFinished(
                    aggregateId1,
                    correlationId1,
                    now,
                    AggregateId("acc-from"),
                    AggregateId("acc-to"),
                ),
            )

        val successAggregate = BankTransactionAggregate.fromEvents(aggregateId1, correlationId1, successEvents)

        assertThat(successAggregate.bankTransaction.status).isEqualTo(BankTransactionStatus.FINISHED)
        assertThat(successAggregate.bankTransaction.moneyWithdrawn).isTrue()
        assertThat(successAggregate.bankTransaction.moneyDeposited).isTrue()
        assertThat(successAggregate.bankTransaction.version).isEqualTo(3L)
        assertThat(successAggregate.domainEvents).isEmpty()

        // Test failed transaction rebuild with rollback
        val aggregateId2 = AggregateId("txn-rebuild-002")
        val correlationId2 = "corr-rebuild-002"
        val failedEvents =
            listOf(
                BankTransactionEvent.BankTransactionCreated(
                    aggregateId2,
                    correlationId2,
                    now,
                    AggregateId("acc-from"),
                    AggregateId("acc-to"),
                    BigDecimal("100.00"),
                ),
                BankTransactionEvent.BankTransactionMoneyWithdrawn(
                    aggregateId2,
                    correlationId2,
                    now,
                    AggregateId("acc-from"),
                    AggregateId("acc-to"),
                    BigDecimal("100.00"),
                ),
                BankTransactionEvent.BankTransactionWithdrawRolledBack(
                    aggregateId2,
                    correlationId2,
                    now,
                    AggregateId("acc-from"),
                    AggregateId("acc-to"),
                    BigDecimal("100.00"),
                ),
                BankTransactionEvent.BankTransactionRolledBack(
                    aggregateId2,
                    correlationId2,
                    now,
                    AggregateId("acc-from"),
                    AggregateId("acc-to"),
                    BigDecimal("100.00"),
                ),
            )

        val failedAggregate = BankTransactionAggregate.fromEvents(aggregateId2, correlationId2, failedEvents)

        assertThat(failedAggregate.bankTransaction.status).isEqualTo(BankTransactionStatus.FAILED)
        assertThat(failedAggregate.bankTransaction.moneyWithdrawn).isFalse()
        assertThat(failedAggregate.bankTransaction.moneyDeposited).isFalse()
        assertThat(failedAggregate.bankTransaction.rollbackReason).isEqualTo(TransactionRollbackReason.UNKNOWN)
        assertThat(failedAggregate.bankTransaction.version).isEqualTo(3L)
        assertThat(failedAggregate.domainEvents).isEmpty()
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

        // When - Validate deposit (automatically finishes)
        aggregate = aggregate.validateMoneyDeposit(createDepositCommand(aggregate))

        // Then - Transaction completed automatically with consistent state
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

    // ==================== Late Confirmation Compensation Tests ====================

    @Test
    fun `should emit withdraw compensation when late withdraw confirmation arrives on failed transaction`() {
        val aggregate = createTestAggregate()
        val failed = aggregate.cancelBankTransaction(createCancelCommand(aggregate))

        val result = failed.validateMoneyWithdraw(createWithdrawCommand(failed))

        assertThat(result.bankTransaction.status).isEqualTo(BankTransactionStatus.FAILED)
        assertThat(result.bankTransaction.withdrawCompensated).isTrue()
        assertThat(result.domainEvents).hasSize(1)
        assertThat(result.domainEvents.first())
            .isInstanceOf(BankTransactionEvent.BankTransactionWithdrawRolledBack::class.java)
    }

    @Test
    fun `should not emit second withdraw compensation when confirmation is redelivered`() {
        val aggregate = createTestAggregate()
        val failed = aggregate.cancelBankTransaction(createCancelCommand(aggregate))
        val compensated = failed.validateMoneyWithdraw(createWithdrawCommand(failed))

        val result = compensated.validateMoneyWithdraw(createWithdrawCommand(compensated))

        assertThat(result.domainEvents).isEmpty()
        assertThat(result.bankTransaction.withdrawCompensated).isTrue()
    }

    @Test
    fun `should emit deposit compensation when late deposit confirmation arrives on failed transaction`() {
        val aggregate = createTestAggregate()
        val failed = aggregate.cancelBankTransaction(createCancelCommand(aggregate))

        val result = failed.validateMoneyDeposit(createDepositCommand(failed))

        assertThat(result.bankTransaction.status).isEqualTo(BankTransactionStatus.FAILED)
        assertThat(result.bankTransaction.depositCompensated).isTrue()
        assertThat(result.domainEvents).hasSize(1)
        assertThat(result.domainEvents.first())
            .isInstanceOf(BankTransactionEvent.BankTransactionDepositRolledBack::class.java)
    }

    @Test
    fun `should not emit second deposit compensation when confirmation is redelivered`() {
        val aggregate = createTestAggregate()
        val failed = aggregate.cancelBankTransaction(createCancelCommand(aggregate))
        val compensated = failed.validateMoneyDeposit(createDepositCommand(failed))

        val result = compensated.validateMoneyDeposit(createDepositCommand(compensated))

        assertThat(result.domainEvents).isEmpty()
        assertThat(result.bankTransaction.depositCompensated).isTrue()
    }

    @Test
    fun `should acknowledge late withdraw confirmation on finished transaction`() {
        var aggregate = createTestAggregate()
        aggregate = aggregate.validateMoneyWithdraw(createWithdrawCommand(aggregate))
        aggregate = aggregate.validateMoneyDeposit(createDepositCommand(aggregate))
        assertThat(aggregate.bankTransaction.status).isEqualTo(BankTransactionStatus.FINISHED)

        val result = aggregate.validateMoneyWithdraw(createWithdrawCommand(aggregate))

        assertThat(result.domainEvents).isEmpty()
        assertThat(result.bankTransaction.status).isEqualTo(BankTransactionStatus.FINISHED)
    }

    @Test
    fun `should acknowledge finish on failed transaction`() {
        val aggregate = createTestAggregate()
        val failed = aggregate.cancelBankTransaction(createCancelCommand(aggregate))

        val result = failed.finishBankTransaction(createFinishCommand(failed))

        assertThat(result.domainEvents).isEmpty()
        assertThat(result.bankTransaction.status).isEqualTo(BankTransactionStatus.FAILED)
    }

    @Test
    fun `should mark withdraw compensated when cancel rolls back confirmed withdraw`() {
        var aggregate = createTestAggregate()
        aggregate = aggregate.validateMoneyWithdraw(createWithdrawCommand(aggregate))

        val result = aggregate.cancelBankTransaction(createCancelCommand(aggregate))

        assertThat(result.bankTransaction.withdrawCompensated).isTrue()
        assertThat(result.domainEvents).hasSize(2)
        assertThat(result.domainEvents[0])
            .isInstanceOf(BankTransactionEvent.BankTransactionWithdrawRolledBack::class.java)
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
            accountId = aggregate.bankTransaction.fromAccountId,
            correlationId = aggregate.bankTransaction.correlationId,
        )

    private fun createDepositCommand(aggregate: BankTransactionAggregate): BankTransactionCommand.ValidateBankTransactionMoneyDeposit =
        BankTransactionCommand.ValidateBankTransactionMoneyDeposit(
            aggregateId = aggregate.bankTransaction.aggregateId,
            accountId = aggregate.bankTransaction.toAccountId,
            correlationId = aggregate.bankTransaction.correlationId,
        )

    private fun createCancelCommand(aggregate: BankTransactionAggregate): BankTransactionCommand.CancelBankTransaction =
        BankTransactionCommand.CancelBankTransaction(
            aggregateId = aggregate.bankTransaction.aggregateId,
            correlationId = aggregate.bankTransaction.correlationId,
            fromAccountId = aggregate.bankTransaction.fromAccountId,
            toAccountId = aggregate.bankTransaction.toAccountId,
            amount = aggregate.bankTransaction.amount,
        )

    private fun createFinishCommand(aggregate: BankTransactionAggregate): BankTransactionCommand.FinishBankTransaction =
        BankTransactionCommand.FinishBankTransaction(
            aggregateId = aggregate.bankTransaction.aggregateId,
            correlationId = aggregate.bankTransaction.correlationId,
            fromAccountId = aggregate.bankTransaction.fromAccountId,
            toAccountId = aggregate.bankTransaction.toAccountId,
        )
}
