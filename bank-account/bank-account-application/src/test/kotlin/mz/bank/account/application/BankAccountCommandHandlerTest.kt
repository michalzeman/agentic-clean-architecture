package mz.bank.account.application

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlinx.coroutines.runBlocking
import mz.bank.account.domain.BankAccount
import mz.bank.account.domain.BankAccountCommand
import mz.bank.account.domain.Email
import mz.shared.domain.AggregateId
import mz.shared.domain.LockProvider
import mz.shared.domain.ObservabilityMeterNames
import mz.shared.domain.ObservabilityMeters
import mz.shared.domain.OperationOutcome
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.messaging.Message
import org.springframework.messaging.support.GenericMessage
import java.math.BigDecimal
import java.time.Instant

class BankAccountCommandHandlerTest {
    private lateinit var bankAccountRepository: BankAccountRepository
    private lateinit var lockProvider: LockProvider
    private lateinit var createBankAccountUseCase: CreateBankAccountUseCase
    private lateinit var commandHandler: BankAccountCommandHandler
    private lateinit var meterRegistry: SimpleMeterRegistry

    @BeforeEach
    fun setUp() {
        bankAccountRepository = mock()
        lockProvider = FakeLockProvider()
        createBankAccountUseCase = CreateBankAccountUseCase(bankAccountRepository)
        meterRegistry = SimpleMeterRegistry()
        commandHandler =
            BankAccountCommandHandler(
                bankAccountRepository,
                lockProvider,
                createBankAccountUseCase,
                FakeObservabilityMeters(meterRegistry),
            )
    }

    // ==================== CreateAccount Tests ====================

    @Test
    fun `should create account successfully`(): Unit =
        runBlocking {
            // Given
            val command =
                BankAccountCommand.CreateAccount(
                    email = Email("test@example.com"),
                    initialBalance = BigDecimal("100.00"),
                )

            whenever(bankAccountRepository.existsByEmail(command.email)).thenReturn(false)
            whenever(bankAccountRepository.upsert(any())).thenAnswer { invocation ->
                val aggregate = invocation.getArgument<mz.bank.account.domain.BankAccountAggregate>(0)
                aggregate.account
            }

            // When
            val result = commandHandler.handle(command)!!

            // Then
            assertThat(result.aggregateId.value).isNotBlank()
            assertThat(result.email).isEqualTo(Email("test@example.com"))
            assertThat(result.amount).isEqualByComparingTo(BigDecimal("100.00"))
            verify(bankAccountRepository).existsByEmail(command.email)
            verify(bankAccountRepository).upsert(any())
        }

    @Test
    fun `should throw exception when creating account with duplicate email`(): Unit =
        runBlocking {
            // Given
            val command =
                BankAccountCommand.CreateAccount(
                    email = Email("duplicate@example.com"),
                    initialBalance = BigDecimal("100.00"),
                )

            whenever(bankAccountRepository.existsByEmail(command.email)).thenReturn(true)

            // When & Then
            assertThatThrownBy { runBlocking { commandHandler.handle(command) } }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("Account with email duplicate@example.com already exists")
        }

    // ==================== DepositMoney Tests ====================

    @Test
    fun `should deposit money to existing account`(): Unit =
        runBlocking {
            // Given
            val aggregateId = AggregateId("acc-deposit-test")
            val existingAccount = createBankAccount(aggregateId, BigDecimal("100.00"))
            val command =
                BankAccountCommand.DepositMoney(
                    aggregateId = aggregateId,
                    amount = BigDecimal("50.00"),
                )
            val expectedAccount = createBankAccount(aggregateId, BigDecimal("150.00"), version = 1L)

            whenever(bankAccountRepository.findById(aggregateId)).thenReturn(existingAccount)
            whenever(bankAccountRepository.upsert(any())).thenReturn(expectedAccount)

            // When
            val result = commandHandler.handle(command)!!

            // Then
            assertThat(result.amount).isEqualByComparingTo(BigDecimal("150.00"))
            verify(bankAccountRepository).findById(aggregateId)
            verify(bankAccountRepository).upsert(any())

            val counter =
                meterRegistry
                    .find(ObservabilityMeterNames.ACCOUNT_OPERATION)
                    .tag("op", "deposit")
                    .tag("outcome", "ok")
                    .counter()
            assertThat(counter).isNotNull
            assertThat(counter!!.count()).isEqualTo(1.0)
        }

    @Test
    fun `should throw exception when depositing to non-existent account`(): Unit =
        runBlocking {
            // Given
            val aggregateId = AggregateId("acc-not-found")
            val command =
                BankAccountCommand.DepositMoney(
                    aggregateId = aggregateId,
                    amount = BigDecimal("50.00"),
                )

            whenever(bankAccountRepository.findById(aggregateId)).thenReturn(null)

            // When & Then
            assertThatThrownBy { runBlocking { commandHandler.handle(command) } }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("not found")
        }

    // ==================== WithdrawMoney Tests ====================

    @Test
    fun `should withdraw money from existing account`(): Unit =
        runBlocking {
            // Given
            val aggregateId = AggregateId("acc-withdraw-test")
            val existingAccount = createBankAccount(aggregateId, BigDecimal("100.00"))
            val command =
                BankAccountCommand.WithdrawMoney(
                    aggregateId = aggregateId,
                    amount = BigDecimal("30.00"),
                )
            val expectedAccount = createBankAccount(aggregateId, BigDecimal("70.00"), version = 1L)

            whenever(bankAccountRepository.findById(aggregateId)).thenReturn(existingAccount)
            whenever(bankAccountRepository.upsert(any())).thenReturn(expectedAccount)

            // When
            val result = commandHandler.handle(command)!!

            // Then
            assertThat(result.amount).isEqualByComparingTo(BigDecimal("70.00"))
        }

    @Test
    fun `should throw exception when withdrawing from non-existent account`(): Unit =
        runBlocking {
            // Given
            val aggregateId = AggregateId("acc-not-found-withdraw")
            val command =
                BankAccountCommand.WithdrawMoney(
                    aggregateId = aggregateId,
                    amount = BigDecimal("50.00"),
                )

            whenever(bankAccountRepository.findById(aggregateId)).thenReturn(null)

            // When & Then
            assertThatThrownBy { runBlocking { commandHandler.handle(command) } }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("not found")
        }

    @Test
    fun `should increment account operation with op withdraw and outcome fail when balance insufficient`(): Unit =
        runBlocking {
            val aggregateId = AggregateId("acc-withdraw-fail-test")
            val existingAccount = createBankAccount(aggregateId, BigDecimal("100.00"))
            val command = BankAccountCommand.WithdrawMoney(aggregateId = aggregateId, amount = BigDecimal("500.00"))

            whenever(bankAccountRepository.findById(aggregateId)).thenReturn(existingAccount)

            assertThatThrownBy { runBlocking { commandHandler.handle(command) } }
                .isInstanceOf(IllegalArgumentException::class.java)

            val counter =
                meterRegistry
                    .find(ObservabilityMeterNames.ACCOUNT_OPERATION)
                    .tag("op", "withdraw")
                    .tag("outcome", "fail")
                    .counter()
            assertThat(counter).isNotNull
            assertThat(counter!!.count()).isEqualTo(1.0)
        }

    // ==================== WithdrawForTransfer Tests ====================

    @Test
    fun `should withdraw for transfer successfully`(): Unit =
        runBlocking {
            // Given
            val aggregateId = AggregateId("acc-transfer-withdraw")
            val existingAccount = createBankAccount(aggregateId, BigDecimal("200.00"))
            val command =
                BankAccountCommand.WithdrawForTransfer(
                    aggregateId = aggregateId,
                    transactionId = "txn-001",
                    amount = BigDecimal("75.00"),
                )
            val expectedAccount =
                createBankAccount(
                    aggregateId,
                    BigDecimal("125.00"),
                    openedTransactions = setOf("txn-001"),
                    version = 1L,
                )

            whenever(bankAccountRepository.findById(aggregateId)).thenReturn(existingAccount)
            whenever(bankAccountRepository.upsert(any())).thenReturn(expectedAccount)

            // When
            val result = commandHandler.handle(command)!!

            // Then
            assertThat(result.amount).isEqualByComparingTo(BigDecimal("125.00"))
            assertThat(result.openedTransactions).contains("txn-001")

            val counter =
                meterRegistry
                    .find(ObservabilityMeterNames.ACCOUNT_OPERATION)
                    .tag("op", "transfer_withdraw")
                    .tag("outcome", "ok")
                    .counter()
            assertThat(counter).isNotNull
            assertThat(counter!!.count()).isEqualTo(1.0)
        }

    // ==================== DepositFromTransfer Tests ====================

    @Test
    fun `should deposit from transfer successfully`(): Unit =
        runBlocking {
            // Given
            val aggregateId = AggregateId("acc-transfer-deposit")
            val existingAccount = createBankAccount(aggregateId, BigDecimal("50.00"))
            val command =
                BankAccountCommand.DepositFromTransfer(
                    aggregateId = aggregateId,
                    transactionId = "txn-002",
                    amount = BigDecimal("100.00"),
                )
            val expectedAccount =
                createBankAccount(
                    aggregateId,
                    BigDecimal("150.00"),
                    openedTransactions = setOf("txn-002"),
                    version = 1L,
                )

            whenever(bankAccountRepository.findById(aggregateId)).thenReturn(existingAccount)
            whenever(bankAccountRepository.upsert(any())).thenReturn(expectedAccount)

            // When
            val result = commandHandler.handle(command)!!

            // Then
            assertThat(result.amount).isEqualByComparingTo(BigDecimal("150.00"))
            assertThat(result.openedTransactions).contains("txn-002")
        }

    // ==================== FinishTransaction Tests ====================

    @Test
    fun `should finish transaction successfully`(): Unit =
        runBlocking {
            // Given
            val aggregateId = AggregateId("acc-finish-txn")
            val existingAccount =
                createBankAccount(
                    aggregateId,
                    BigDecimal("100.00"),
                    openedTransactions = setOf("txn-003"),
                )
            val command =
                BankAccountCommand.FinishTransaction(
                    aggregateId = aggregateId,
                    transactionId = "txn-003",
                )
            val expectedAccount =
                createBankAccount(
                    aggregateId,
                    BigDecimal("100.00"),
                    finishedTransactions = setOf("txn-003"),
                    version = 1L,
                )

            whenever(bankAccountRepository.findById(aggregateId)).thenReturn(existingAccount)
            whenever(bankAccountRepository.upsert(any())).thenReturn(expectedAccount)

            // When
            val result = commandHandler.handle(command)!!

            // Then
            assertThat(result.openedTransactions).doesNotContain("txn-003")
            assertThat(result.finishedTransactions).contains("txn-003")
        }

    // ==================== HandleAsync Tests ====================

    @Test
    fun `should handle async command from message channel for deposit`(): Unit =
        runBlocking {
            // Given
            val aggregateId = AggregateId("acc-async-deposit")
            val existingAccount = createBankAccount(aggregateId, BigDecimal("100.00"))
            val command =
                BankAccountCommand.DepositMoney(
                    aggregateId = aggregateId,
                    amount = BigDecimal("50.00"),
                )
            val message: Message<BankAccountCommand> = GenericMessage(command)
            val expectedAccount = createBankAccount(aggregateId, BigDecimal("150.00"), version = 1L)

            whenever(bankAccountRepository.findById(aggregateId)).thenReturn(existingAccount)
            whenever(bankAccountRepository.upsert(any())).thenReturn(expectedAccount)

            // When
            commandHandler.handleAsync(message)

            // Then
            verify(bankAccountRepository).findById(aggregateId)
            verify(bankAccountRepository).upsert(any())
        }

    @Test
    fun `should handle async command from message channel for create account`(): Unit =
        runBlocking {
            // Given
            val command =
                BankAccountCommand.CreateAccount(
                    email = Email("async@example.com"),
                    initialBalance = BigDecimal("200.00"),
                )
            val message: Message<BankAccountCommand> = GenericMessage(command)

            whenever(bankAccountRepository.existsByEmail(command.email)).thenReturn(false)
            whenever(bankAccountRepository.upsert(any())).thenAnswer { invocation ->
                val aggregate = invocation.getArgument<mz.bank.account.domain.BankAccountAggregate>(0)
                aggregate.account
            }

            // When
            commandHandler.handleAsync(message)

            // Then
            verify(bankAccountRepository).existsByEmail(command.email)
            verify(bankAccountRepository).upsert(any())
        }

    @Test
    fun `should handle async command from message channel for withdraw`(): Unit =
        runBlocking {
            // Given
            val aggregateId = AggregateId("acc-async-withdraw")
            val existingAccount = createBankAccount(aggregateId, BigDecimal("100.00"))
            val command =
                BankAccountCommand.WithdrawMoney(
                    aggregateId = aggregateId,
                    amount = BigDecimal("30.00"),
                )
            val message: Message<BankAccountCommand> = GenericMessage(command)
            val expectedAccount = createBankAccount(aggregateId, BigDecimal("70.00"), version = 1L)

            whenever(bankAccountRepository.findById(aggregateId)).thenReturn(existingAccount)
            whenever(bankAccountRepository.upsert(any())).thenReturn(expectedAccount)

            // When
            commandHandler.handleAsync(message)

            // Then
            verify(bankAccountRepository).findById(aggregateId)
            verify(bankAccountRepository).upsert(any())
        }

    // ==================== Helper Methods ====================

    private fun createBankAccount(
        aggregateId: AggregateId,
        amount: BigDecimal,
        email: String = "test@example.com",
        openedTransactions: Set<String> = emptySet(),
        finishedTransactions: Set<String> = emptySet(),
        version: Long = 0L,
    ): BankAccount {
        val now = Instant.now()
        return BankAccount(
            aggregateId = aggregateId,
            email = Email(email),
            amount = amount,
            openedTransactions = openedTransactions,
            finishedTransactions = finishedTransactions,
            version = version,
            createdAt = now,
            updatedAt = now,
        )
    }

    /**
     * Fake LockProvider for testing that executes operations directly without actual locking.
     */
    private class FakeLockProvider : LockProvider {
        override suspend fun <T> withLock(
            keyLock: String,
            operation: suspend () -> T,
        ): T = operation()
    }

    /**
     * Implements the [ObservabilityMeters] port from shared-domain, so these application-layer
     * tests do not depend on the connector-observability adapter.
     * Delegates to a real [SimpleMeterRegistry], so assertions verify metric semantics
     * (counter name, tags, count) instead of mock interactions.
     * The fake intentionally omits the production implementation's error resilience,
     * so failures surface in tests.
     */
    private class FakeObservabilityMeters(
        private val meterRegistry: SimpleMeterRegistry,
    ) : ObservabilityMeters {
        override fun transferCompleted(outcome: OperationOutcome) {
            meterRegistry.counter(ObservabilityMeterNames.TRANSFER_COMPLETED, "outcome", outcome.tagValue).increment()
        }

        override fun transferRolledBack(reason: String) {
            meterRegistry.counter(ObservabilityMeterNames.TRANSFER_ROLLED_BACK, "reason", reason).increment()
        }

        override fun accountOperation(
            op: String,
            outcome: OperationOutcome,
        ) {
            meterRegistry.counter(ObservabilityMeterNames.ACCOUNT_OPERATION, "op", op, "outcome", outcome.tagValue).increment()
        }
    }
}
