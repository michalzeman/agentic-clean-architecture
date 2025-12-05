package mz.bank.account.domain

import mz.shared.domain.AggregateId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

class BankAccountAggregateTest {
    // ==================== Account Creation Tests ====================

    @Test
    fun `should create account with valid initial balance`() {
        // Given
        val cmd =
            BankAccountCommand.CreateAccount(
                aggregateId = AggregateId("acc-123"),
                initialBalance = BigDecimal("100.00"),
            )

        // When
        val aggregate = BankAccountAggregate.create(cmd)

        // Then
        assertThat(aggregate.account.aggregateId).isEqualTo(AggregateId("acc-123"))
        assertThat(aggregate.account.amount).isEqualByComparingTo(BigDecimal("100.00"))
        assertThat(aggregate.domainEvents).hasSize(1)
        assertThat(aggregate.domainEvents.first()).isInstanceOf(BankAccountEvent.AccountCreated::class.java)
    }

    @Test
    fun `should create account with zero initial balance`() {
        // Given
        val cmd =
            BankAccountCommand.CreateAccount(
                aggregateId = AggregateId("acc-456"),
                initialBalance = BigDecimal.ZERO,
            )

        // When
        val aggregate = BankAccountAggregate.create(cmd)

        // Then
        assertThat(aggregate.account.amount).isEqualByComparingTo(BigDecimal.ZERO)
        assertThat(aggregate.domainEvents).hasSize(1)
        assertThat(aggregate.account.openedTransactions).isEmpty()
        assertThat(aggregate.account.finishedTransactions).isEmpty()
    }

    @Test
    fun `should fail to create account with negative initial balance`() {
        // Given
        val cmd =
            BankAccountCommand.CreateAccount(
                aggregateId = AggregateId("acc-789"),
                initialBalance = BigDecimal("-50.00"),
            )

        // When & Then
        assertThatThrownBy { BankAccountAggregate.create(cmd) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Initial balance cannot be negative")
    }

    // ==================== Deposit Money Tests ====================

    @Test
    fun `should deposit positive amount successfully`() {
        // Given
        val aggregate =
            BankAccountAggregate.create(
                BankAccountCommand.CreateAccount(
                    aggregateId = AggregateId("acc-deposit"),
                    initialBalance = BigDecimal("100.00"),
                ),
            )
        val depositCmd =
            BankAccountCommand.DepositMoney(
                aggregateId = AggregateId("acc-deposit"),
                amount = BigDecimal("50.00"),
            )

        // When
        val result = aggregate.deposit(depositCmd)

        // Then
        assertThat(result.account.amount).isEqualByComparingTo(BigDecimal("150.00"))
        assertThat(result.domainEvents).hasSize(1)
        assertThat(result.domainEvents.first()).isInstanceOf(BankAccountEvent.MoneyDeposited::class.java)
    }

    @Test
    fun `should fail to deposit zero amount`() {
        // Given
        val aggregate =
            BankAccountAggregate.create(
                BankAccountCommand.CreateAccount(
                    aggregateId = AggregateId("acc-deposit-zero"),
                    initialBalance = BigDecimal("100.00"),
                ),
            )
        val depositCmd =
            BankAccountCommand.DepositMoney(
                aggregateId = AggregateId("acc-deposit-zero"),
                amount = BigDecimal.ZERO,
            )

        // When & Then
        assertThatThrownBy { aggregate.deposit(depositCmd) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Deposit amount must be positive")
    }

    @Test
    fun `should fail to deposit negative amount`() {
        // Given
        val aggregate =
            BankAccountAggregate.create(
                BankAccountCommand.CreateAccount(
                    aggregateId = AggregateId("acc-deposit-neg"),
                    initialBalance = BigDecimal("100.00"),
                ),
            )
        val depositCmd =
            BankAccountCommand.DepositMoney(
                aggregateId = AggregateId("acc-deposit-neg"),
                amount = BigDecimal("-25.00"),
            )

        // When & Then
        assertThatThrownBy { aggregate.deposit(depositCmd) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Deposit amount must be positive")
    }

    // ==================== Withdraw Money Tests ====================

    @Test
    fun `should withdraw money when sufficient balance`() {
        // Given
        val aggregate =
            BankAccountAggregate.create(
                BankAccountCommand.CreateAccount(
                    aggregateId = AggregateId("acc-withdraw"),
                    initialBalance = BigDecimal("100.00"),
                ),
            )
        val withdrawCmd =
            BankAccountCommand.WithdrawMoney(
                aggregateId = AggregateId("acc-withdraw"),
                amount = BigDecimal("30.00"),
            )

        // When
        val result = aggregate.withdraw(withdrawCmd)

        // Then
        assertThat(result.account.amount).isEqualByComparingTo(BigDecimal("70.00"))
        assertThat(result.domainEvents).hasSize(1)
        assertThat(result.domainEvents.first()).isInstanceOf(BankAccountEvent.MoneyWithdrawn::class.java)
    }

    @Test
    fun `should fail to withdraw more than available balance`() {
        // Given
        val aggregate =
            BankAccountAggregate.create(
                BankAccountCommand.CreateAccount(
                    aggregateId = AggregateId("acc-insufficient"),
                    initialBalance = BigDecimal("50.00"),
                ),
            )
        val withdrawCmd =
            BankAccountCommand.WithdrawMoney(
                aggregateId = AggregateId("acc-insufficient"),
                amount = BigDecimal("100.00"),
            )

        // When & Then
        assertThatThrownBy { aggregate.withdraw(withdrawCmd) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Insufficient balance")
    }

    @Test
    fun `should fail to withdraw zero amount`() {
        // Given
        val aggregate =
            BankAccountAggregate.create(
                BankAccountCommand.CreateAccount(
                    aggregateId = AggregateId("acc-withdraw-zero"),
                    initialBalance = BigDecimal("100.00"),
                ),
            )
        val withdrawCmd =
            BankAccountCommand.WithdrawMoney(
                aggregateId = AggregateId("acc-withdraw-zero"),
                amount = BigDecimal.ZERO,
            )

        // When & Then
        assertThatThrownBy { aggregate.withdraw(withdrawCmd) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Withdrawal amount must be positive")
    }

    @Test
    fun `should fail to withdraw negative amount`() {
        // Given
        val aggregate =
            BankAccountAggregate.create(
                BankAccountCommand.CreateAccount(
                    aggregateId = AggregateId("acc-withdraw-neg"),
                    initialBalance = BigDecimal("100.00"),
                ),
            )
        val withdrawCmd =
            BankAccountCommand.WithdrawMoney(
                aggregateId = AggregateId("acc-withdraw-neg"),
                amount = BigDecimal("-20.00"),
            )

        // When & Then
        assertThatThrownBy { aggregate.withdraw(withdrawCmd) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Withdrawal amount must be positive")
    }

    @Test
    fun `should withdraw exact balance successfully`() {
        // Given
        val aggregate =
            BankAccountAggregate.create(
                BankAccountCommand.CreateAccount(
                    aggregateId = AggregateId("acc-exact"),
                    initialBalance = BigDecimal("100.00"),
                ),
            )
        val withdrawCmd =
            BankAccountCommand.WithdrawMoney(
                aggregateId = AggregateId("acc-exact"),
                amount = BigDecimal("100.00"),
            )

        // When
        val result = aggregate.withdraw(withdrawCmd)

        // Then
        assertThat(result.account.amount).isEqualByComparingTo(BigDecimal.ZERO)
        assertThat(result.domainEvents).hasSize(1)
    }

    // ==================== Transfer Withdrawal Tests ====================

    @Test
    fun `should withdraw transfer money with new transaction`() {
        // Given
        val aggregate =
            BankAccountAggregate.create(
                BankAccountCommand.CreateAccount(
                    aggregateId = AggregateId("acc-transfer-src"),
                    initialBalance = BigDecimal("200.00"),
                ),
            )
        val withdrawCmd =
            BankAccountCommand.WithdrawForTransfer(
                aggregateId = AggregateId("acc-transfer-src"),
                transactionId = "txn-001",
                amount = BigDecimal("50.00"),
            )

        // When
        val result = aggregate.withdrawForTransfer(withdrawCmd)

        // Then
        assertThat(result.account.amount).isEqualByComparingTo(BigDecimal("150.00"))
        assertThat(result.account.openedTransactions).contains("txn-001")
        assertThat(result.account.finishedTransactions).doesNotContain("txn-001")
        assertThat(result.domainEvents).hasSize(1)
        assertThat(result.domainEvents.first()).isInstanceOf(BankAccountEvent.TransferWithdrawalStarted::class.java)
    }

    @Test
    fun `should fail transfer withdrawal when insufficient balance`() {
        // Given
        val aggregate =
            BankAccountAggregate.create(
                BankAccountCommand.CreateAccount(
                    aggregateId = AggregateId("acc-transfer-insufficient"),
                    initialBalance = BigDecimal("30.00"),
                ),
            )
        val withdrawCmd =
            BankAccountCommand.WithdrawForTransfer(
                aggregateId = AggregateId("acc-transfer-insufficient"),
                transactionId = "txn-002",
                amount = BigDecimal("100.00"),
            )

        // When & Then
        assertThatThrownBy { aggregate.withdrawForTransfer(withdrawCmd) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Insufficient balance for transfer")
    }

    @Test
    fun `should fail transfer withdrawal when transaction already opened`() {
        // Given
        val createCmd =
            BankAccountCommand.CreateAccount(
                aggregateId = AggregateId("acc-transfer-opened"),
                initialBalance = BigDecimal("200.00"),
            )
        val aggregate = BankAccountAggregate.create(createCmd)

        // First withdrawal to open transaction
        val firstWithdrawal =
            BankAccountCommand.WithdrawForTransfer(
                aggregateId = AggregateId("acc-transfer-opened"),
                transactionId = "txn-003",
                amount = BigDecimal("50.00"),
            )
        val aggregateAfterFirst = aggregate.withdrawForTransfer(firstWithdrawal)

        // Try to withdraw same transaction again
        val secondWithdrawal =
            BankAccountCommand.WithdrawForTransfer(
                aggregateId = AggregateId("acc-transfer-opened"),
                transactionId = "txn-003",
                amount = BigDecimal("30.00"),
            )

        // When & Then
        assertThatThrownBy { aggregateAfterFirst.withdrawForTransfer(secondWithdrawal) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("is already opened")
    }

    @Test
    fun `should fail transfer withdrawal when transaction already finished`() {
        // Given
        val createCmd =
            BankAccountCommand.CreateAccount(
                aggregateId = AggregateId("acc-transfer-finished"),
                initialBalance = BigDecimal("200.00"),
            )
        val aggregate = BankAccountAggregate.create(createCmd)

        // Withdraw for transfer
        val withdrawCmd =
            BankAccountCommand.WithdrawForTransfer(
                aggregateId = AggregateId("acc-transfer-finished"),
                transactionId = "txn-004",
                amount = BigDecimal("50.00"),
            )
        val afterWithdraw = aggregate.withdrawForTransfer(withdrawCmd)

        // Finish transaction
        val finishCmd =
            BankAccountCommand.FinishTransaction(
                aggregateId = AggregateId("acc-transfer-finished"),
                transactionId = "txn-004",
            )
        val afterFinish = afterWithdraw.finishTransaction(finishCmd)

        // Try to withdraw with finished transaction
        val retryWithdraw =
            BankAccountCommand.WithdrawForTransfer(
                aggregateId = AggregateId("acc-transfer-finished"),
                transactionId = "txn-004",
                amount = BigDecimal("30.00"),
            )

        // When & Then
        assertThatThrownBy { afterFinish.withdrawForTransfer(retryWithdraw) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("is already finished")
    }

    // ==================== Transfer Deposit Tests ====================

    @Test
    fun `should deposit transfer money with new transaction`() {
        // Given
        val aggregate =
            BankAccountAggregate.create(
                BankAccountCommand.CreateAccount(
                    aggregateId = AggregateId("acc-transfer-dst"),
                    initialBalance = BigDecimal("50.00"),
                ),
            )
        val depositCmd =
            BankAccountCommand.DepositFromTransfer(
                aggregateId = AggregateId("acc-transfer-dst"),
                transactionId = "txn-101",
                amount = BigDecimal("75.00"),
            )

        // When
        val result = aggregate.depositFromTransfer(depositCmd)

        // Then
        assertThat(result.account.amount).isEqualByComparingTo(BigDecimal("125.00"))
        assertThat(result.account.openedTransactions).contains("txn-101")
        assertThat(result.account.finishedTransactions).doesNotContain("txn-101")
        assertThat(result.domainEvents).hasSize(1)
        assertThat(result.domainEvents.first()).isInstanceOf(BankAccountEvent.TransferDepositStarted::class.java)
    }

    @Test
    fun `should fail transfer deposit when transaction already opened`() {
        // Given
        val createCmd =
            BankAccountCommand.CreateAccount(
                aggregateId = AggregateId("acc-transfer-dep-opened"),
                initialBalance = BigDecimal("50.00"),
            )
        val aggregate = BankAccountAggregate.create(createCmd)

        // First deposit to open transaction
        val firstDeposit =
            BankAccountCommand.DepositFromTransfer(
                aggregateId = AggregateId("acc-transfer-dep-opened"),
                transactionId = "txn-102",
                amount = BigDecimal("75.00"),
            )
        val aggregateAfterFirst = aggregate.depositFromTransfer(firstDeposit)

        // Try to deposit same transaction again
        val secondDeposit =
            BankAccountCommand.DepositFromTransfer(
                aggregateId = AggregateId("acc-transfer-dep-opened"),
                transactionId = "txn-102",
                amount = BigDecimal("50.00"),
            )

        // When & Then
        assertThatThrownBy { aggregateAfterFirst.depositFromTransfer(secondDeposit) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("is already opened")
    }

    @Test
    fun `should fail transfer deposit when transaction already finished`() {
        // Given
        val createCmd =
            BankAccountCommand.CreateAccount(
                aggregateId = AggregateId("acc-transfer-dep-finished"),
                initialBalance = BigDecimal("50.00"),
            )
        val aggregate = BankAccountAggregate.create(createCmd)

        // Deposit for transfer
        val depositCmd =
            BankAccountCommand.DepositFromTransfer(
                aggregateId = AggregateId("acc-transfer-dep-finished"),
                transactionId = "txn-103",
                amount = BigDecimal("75.00"),
            )
        val afterDeposit = aggregate.depositFromTransfer(depositCmd)

        // Finish transaction
        val finishCmd =
            BankAccountCommand.FinishTransaction(
                aggregateId = AggregateId("acc-transfer-dep-finished"),
                transactionId = "txn-103",
            )
        val afterFinish = afterDeposit.finishTransaction(finishCmd)

        // Try to deposit with finished transaction
        val retryDeposit =
            BankAccountCommand.DepositFromTransfer(
                aggregateId = AggregateId("acc-transfer-dep-finished"),
                transactionId = "txn-103",
                amount = BigDecimal("50.00"),
            )

        // When & Then
        assertThatThrownBy { afterFinish.depositFromTransfer(retryDeposit) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("is already finished")
    }

    // ==================== Finish Transaction Tests ====================

    @Test
    fun `should finish opened transaction successfully`() {
        // Given
        val aggregate =
            BankAccountAggregate.create(
                BankAccountCommand.CreateAccount(
                    aggregateId = AggregateId("acc-finish"),
                    initialBalance = BigDecimal("200.00"),
                ),
            )
        val withdrawCmd =
            BankAccountCommand.WithdrawForTransfer(
                aggregateId = AggregateId("acc-finish"),
                transactionId = "txn-201",
                amount = BigDecimal("50.00"),
            )
        val afterWithdraw = aggregate.withdrawForTransfer(withdrawCmd)

        val finishCmd =
            BankAccountCommand.FinishTransaction(
                aggregateId = AggregateId("acc-finish"),
                transactionId = "txn-201",
            )

        // When
        val result = afterWithdraw.finishTransaction(finishCmd)

        // Then
        assertThat(result.account.openedTransactions).doesNotContain("txn-201")
        assertThat(result.account.finishedTransactions).contains("txn-201")
        assertThat(result.domainEvents).hasSize(1)
        assertThat(result.domainEvents.first()).isInstanceOf(BankAccountEvent.TransactionFinished::class.java)
    }

    @Test
    fun `should fail to finish transaction not in opened set`() {
        // Given
        val aggregate =
            BankAccountAggregate.create(
                BankAccountCommand.CreateAccount(
                    aggregateId = AggregateId("acc-finish-not-opened"),
                    initialBalance = BigDecimal("200.00"),
                ),
            )
        val finishCmd =
            BankAccountCommand.FinishTransaction(
                aggregateId = AggregateId("acc-finish-not-opened"),
                transactionId = "txn-202",
            )

        // When & Then
        assertThatThrownBy { aggregate.finishTransaction(finishCmd) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("is not in opened set")
    }

    @Test
    fun `should fail to finish already finished transaction`() {
        // Given
        val aggregate =
            BankAccountAggregate.create(
                BankAccountCommand.CreateAccount(
                    aggregateId = AggregateId("acc-finish-already"),
                    initialBalance = BigDecimal("200.00"),
                ),
            )
        val withdrawCmd =
            BankAccountCommand.WithdrawForTransfer(
                aggregateId = AggregateId("acc-finish-already"),
                transactionId = "txn-203",
                amount = BigDecimal("50.00"),
            )
        val afterWithdraw = aggregate.withdrawForTransfer(withdrawCmd)

        val finishCmd =
            BankAccountCommand.FinishTransaction(
                aggregateId = AggregateId("acc-finish-already"),
                transactionId = "txn-203",
            )
        val afterFinish = afterWithdraw.finishTransaction(finishCmd)

        // Try to finish again
        val retryFinish =
            BankAccountCommand.FinishTransaction(
                aggregateId = AggregateId("acc-finish-already"),
                transactionId = "txn-203",
            )

        // When & Then
        assertThatThrownBy { afterFinish.finishTransaction(retryFinish) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("is not in opened set")
    }

    // ==================== Event Application Tests ====================

    @Test
    fun `should rebuild state from events correctly`() {
        // Given
        val aggregateId = AggregateId("acc-rebuild")
        val now = Instant.now()
        val events =
            listOf(
                BankAccountEvent.AccountCreated(aggregateId, now, BigDecimal("100.00")),
                BankAccountEvent.MoneyDeposited(aggregateId, now, BigDecimal("50.00")),
                BankAccountEvent.MoneyWithdrawn(aggregateId, now, BigDecimal("20.00")),
            )

        // When
        val aggregate = BankAccountAggregate.fromEvents(aggregateId, events)

        // Then
        assertThat(aggregate.account.amount).isEqualByComparingTo(BigDecimal("130.00"))
        assertThat(aggregate.account.version).isEqualTo(3L)
        assertThat(aggregate.domainEvents).isEmpty()
    }

    @Test
    fun `should apply all event types correctly`() {
        // Given
        val aggregateId = AggregateId("acc-all-events")
        val now = Instant.now()
        val events =
            listOf(
                BankAccountEvent.AccountCreated(aggregateId, now, BigDecimal("200.00")),
                BankAccountEvent.TransferWithdrawalStarted(aggregateId, now, "txn-events-1", BigDecimal("50.00")),
                BankAccountEvent.TransferDepositStarted(aggregateId, now, "txn-events-2", BigDecimal("30.00")),
                BankAccountEvent.MoneyDeposited(aggregateId, now, BigDecimal("100.00")),
                BankAccountEvent.TransactionFinished(aggregateId, now, "txn-events-1"),
                BankAccountEvent.TransactionFinished(aggregateId, now, "txn-events-2"),
                BankAccountEvent.MoneyWithdrawn(aggregateId, now, BigDecimal("20.00")),
            )

        // When
        val aggregate = BankAccountAggregate.fromEvents(aggregateId, events)

        // Then
        // Balance: 200 - 50 + 30 + 100 - 20 = 260
        assertThat(aggregate.account.amount).isEqualByComparingTo(BigDecimal("260.00"))
        assertThat(aggregate.account.openedTransactions).isEmpty()
        assertThat(aggregate.account.finishedTransactions).containsExactly("txn-events-1", "txn-events-2")
        assertThat(aggregate.account.version).isEqualTo(7L)
    }

    // ==================== Invariants Tests ====================

    @Test
    fun `should maintain non-negative balance invariant`() {
        // Given
        val aggregate =
            BankAccountAggregate.create(
                BankAccountCommand.CreateAccount(
                    aggregateId = AggregateId("acc-invariant"),
                    initialBalance = BigDecimal("100.00"),
                ),
            )

        // When
        val result =
            aggregate.withdraw(
                BankAccountCommand.WithdrawMoney(
                    aggregateId = AggregateId("acc-invariant"),
                    amount = BigDecimal("100.00"),
                ),
            )

        // Then
        assertThat(result.account.amount).isGreaterThanOrEqualTo(BigDecimal.ZERO)
    }

    @Test
    fun `should maintain no overlap between opened and finished transactions`() {
        // Given
        val aggregateId = AggregateId("acc-no-overlap")
        val now = Instant.now()
        val events =
            listOf(
                BankAccountEvent.AccountCreated(aggregateId, now, BigDecimal("200.00")),
                BankAccountEvent.TransferWithdrawalStarted(aggregateId, now, "txn-overlap-1", BigDecimal("50.00")),
                BankAccountEvent.TransactionFinished(aggregateId, now, "txn-overlap-1"),
            )

        // When
        val aggregate = BankAccountAggregate.fromEvents(aggregateId, events)

        // Then
        assertThat(aggregate.account.openedTransactions).doesNotContainAnyElementsOf(
            aggregate.account.finishedTransactions,
        )
    }

    @Test
    fun `should create account with proper initial state`() {
        // Given
        val cmd =
            BankAccountCommand.CreateAccount(
                aggregateId = AggregateId("acc-initial"),
                initialBalance = BigDecimal("250.00"),
            )

        // When
        val aggregate = BankAccountAggregate.create(cmd)

        // Then
        assertThat(aggregate.account.aggregateId).isEqualTo(AggregateId("acc-initial"))
        assertThat(aggregate.account.amount).isEqualByComparingTo(BigDecimal("250.00"))
        assertThat(aggregate.account.openedTransactions).isEmpty()
        assertThat(aggregate.account.finishedTransactions).isEmpty()
        assertThat(aggregate.account.version).isEqualTo(0L)
    }

    @Test
    fun `should track version increments on each change`() {
        // Given
        val cmd =
            BankAccountCommand.CreateAccount(
                aggregateId = AggregateId("acc-version"),
                initialBalance = BigDecimal("100.00"),
            )
        var aggregate = BankAccountAggregate.create(cmd)
        assertThat(aggregate.account.version).isEqualTo(0L)

        // When & Then - deposit increases version
        aggregate =
            aggregate.deposit(
                BankAccountCommand.DepositMoney(
                    aggregateId = AggregateId("acc-version"),
                    amount = BigDecimal("50.00"),
                ),
            )
        assertThat(aggregate.account.version).isEqualTo(1L)

        // When & Then - withdraw increases version
        aggregate =
            aggregate.withdraw(
                BankAccountCommand.WithdrawMoney(
                    aggregateId = AggregateId("acc-version"),
                    amount = BigDecimal("25.00"),
                ),
            )
        assertThat(aggregate.account.version).isEqualTo(2L)
    }
}
