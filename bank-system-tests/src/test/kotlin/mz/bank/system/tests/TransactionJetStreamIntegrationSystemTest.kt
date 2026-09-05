package mz.bank.system.tests

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import mz.bank.account.contract.proto.BankAccountEvent
import mz.bank.system.tests.wiring.BankSystemTestConfiguration
import mz.shared.connector.nats.jetstream.consumer.JetStreamConsumer
import mz.shared.connector.system.tests.awaitMessages
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.integration.channel.QueueChannel
import org.springframework.messaging.Message
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * System integration test for bank transaction flow via JetStream path.
 * Verifies the JetStream messaging integration is active and functional.
 * Tests the complete transaction lifecycle:
 * 1. Create source and destination accounts via HTTP API
 * 2. Create a transaction via HTTP API
 * 3. Consume bank-transaction events from queue channel
 * 4. Verify transaction finished by checking status
 * 5. Verify account balances via HTTP API
 *
 * Note: Since both Redis and JetStream paths are active in docker-compose,
 * this test verifies the JetStream path works end-to-end without breaking
 * the overall transaction flow.
 */
@Tag("systemChecks")
@SpringBootTest(
    classes = [BankSystemTestConfiguration::class],
)
@TestInstance(Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named = "NATS_JETSTREAM_ENABLED", matches = "true")
class TransactionJetStreamIntegrationSystemTest {
    private val objectMapper = ObjectMapper()

    @Autowired
    private lateinit var accountServiceWebClient: WebClient

    @Autowired
    private lateinit var transactionServiceWebClient: WebClient

    @Autowired
    private lateinit var accountServiceHealthChecker: ServiceHealthChecker

    @Autowired
    private lateinit var transactionServiceHealthChecker: ServiceHealthChecker

    @Autowired
    @Qualifier("bankTransactionEventsNatsStreamChannel")
    private lateinit var bankTransactionEventsChannel: QueueChannel

    @Autowired
    @Qualifier("bankAccountEventsNatsStreamChannel")
    private lateinit var bankAccountEventsChannel: QueueChannel

    // Nullable + required=false so the context still loads if the JetStream consumer
    // beans are absent; the diagnostics then report the beans as missing.
    @Autowired(required = false)
    @Qualifier("bankAccountEventsNatsConsumer")
    private var bankAccountEventsNatsConsumer: JetStreamConsumer<BankAccountEvent>? = null

    @Autowired(required = false)
    @Qualifier("bankTransactionEventsNatsConsumer")
    private var bankTransactionEventsNatsConsumer: JetStreamConsumer<mz.bank.transaction.contract.proto.BankTransactionEvent>? = null

    @BeforeAll
    fun setUp() {
        runBlocking {
            listOf(
                accountServiceHealthChecker,
                transactionServiceHealthChecker,
            ).waitForAllServices()
        }
    }

    @Test
    fun `should complete full transaction flow via JetStream path and verify account balances`() {
        runBlocking {
            val testId = UUID.randomUUID().toString()
            val timestamp = Instant.now().epochSecond

            val sourceAccountEmail = "source-$testId-$timestamp@example.com"
            val sourceInitialBalance = BigDecimal("5000.00")
            val sourceAccountRequest = objectMapper.buildAccountRequest(sourceAccountEmail, sourceInitialBalance)

            val sourceAccount: JsonNode =
                accountServiceWebClient
                    .post()
                    .uri("/api/v1/accounts")
                    .header("Content-Type", "application/json")
                    .bodyValue(sourceAccountRequest)
                    .retrieve()
                    .awaitBody()

            val sourceAccountId = sourceAccount.get("accountId").stringValue()
            assertThat(sourceAccountId).isNotNull
            assertThat(BigDecimal(sourceAccount.get("balance").asText())).isEqualByComparingTo(sourceInitialBalance)

            val destAccountEmail = "dest-$testId-$timestamp@example.com"
            val destInitialBalance = BigDecimal("1000.00")
            val destAccountRequest = objectMapper.buildAccountRequest(destAccountEmail, destInitialBalance)

            val destAccount: JsonNode =
                accountServiceWebClient
                    .post()
                    .uri("/api/v1/accounts")
                    .header("Content-Type", "application/json")
                    .bodyValue(destAccountRequest)
                    .retrieve()
                    .awaitBody()

            val destAccountId = destAccount.get("accountId").stringValue()
            assertThat(destAccountId).isNotNull
            assertThat(BigDecimal(destAccount.get("balance").asText())).isEqualByComparingTo(destInitialBalance)

            // Wait for account creation events to propagate to transaction service
            bankAccountEventsChannel.awaitMessages(
                maxDelayMillis = 30000,
                pollIntervalMillis = 500,
                assertion = { msgs: List<Message<*>> ->
                    val createdAccountIds =
                        msgs.mapNotNull { msg: Message<*> ->
                            val payload = msg.payload as? mz.bank.account.contract.proto.BankAccountEvent
                            payload?.takeIf { it.hasAccountCreated() }?.accountCreated?.aggregateId
                        }
                    createdAccountIds.contains(sourceAccountId) && createdAccountIds.contains(destAccountId)
                },
            )

            val transferAmount = BigDecimal("500.00")
            val transactionRequest = objectMapper.buildTransactionRequest(sourceAccountId, destAccountId, transferAmount)

            // Retry transaction creation with backoff to allow account events to propagate
            val transaction: JsonNode =
                retryWithBackoff(
                    maxRetries = 10,
                    initialDelayMillis = 500,
                ) {
                    transactionServiceWebClient
                        .post()
                        .uri("/api/v1/transactions")
                        .header("Content-Type", "application/json")
                        .bodyValue(transactionRequest)
                        .retrieve()
                        .awaitBody()
                }

            val transactionId = transaction.get("transactionId").stringValue()
            assertThat(transactionId).isNotNull
            assertThat(transaction.get("correlationId").stringValue()).isNotNull
            assertThat(transaction.get("status").stringValue()).isEqualTo("CREATED")
            assertThat(BigDecimal(transaction.get("amount").asText())).isEqualByComparingTo(transferAmount)

            val messages: List<Message<*>> =
                bankTransactionEventsChannel.awaitMessages(
                    maxDelayMillis = 180000,
                    pollIntervalMillis = 500,
                    assertion = { msgs: List<Message<*>> ->
                        msgs.any { msg: Message<*> ->
                            val payload = msg.payload as? mz.bank.transaction.contract.proto.BankTransactionEvent
                            payload?.hasTransactionFinished() == true &&
                                payload.transactionFinished.aggregateId == transactionId
                        }
                    },
                )

            val finishedEvent =
                messages.find { msg: Message<*> ->
                    val payload = msg.payload as? mz.bank.transaction.contract.proto.BankTransactionEvent
                    payload?.hasTransactionFinished() == true &&
                        payload.transactionFinished.aggregateId == transactionId
                }
            // Diagnostics are built only when the event is missing — inside
            // assertFinishedEventPresent — so the happy path stays untouched.
            assertFinishedEventPresent(finishedEvent, messages, transactionId)

            val finalTransaction: JsonNode =
                transactionServiceWebClient
                    .get()
                    .uri("/api/v1/transactions/{transactionId}", transactionId)
                    .retrieve()
                    .awaitBody()

            assertThat(finalTransaction.get("transactionId").stringValue()).isEqualTo(transactionId)
            assertThat(finalTransaction.get("status").stringValue()).isEqualTo("FINISHED")
            assertThat(finalTransaction.get("moneyWithdrawn").asBoolean()).isEqualTo(true)
            assertThat(finalTransaction.get("moneyDeposited").asBoolean()).isEqualTo(true)

            // Poll for account state with finished transaction
            val (finalSourceAccount, finalDestAccount) =
                pollForAccountsWithFinishedTransaction(
                    sourceAccountId = sourceAccountId,
                    destAccountId = destAccountId,
                    transactionId = transactionId,
                    maxRetries = 30,
                    delayMillis = 1000,
                )

            val expectedSourceBalance = sourceInitialBalance.subtract(transferAmount)
            assertThat(finalSourceAccount.get("accountId").stringValue()).isEqualTo(sourceAccountId)
            assertThat(BigDecimal(finalSourceAccount.get("balance").asText())).isEqualByComparingTo(expectedSourceBalance)

            val expectedDestBalance = destInitialBalance.add(transferAmount)
            assertThat(finalDestAccount.get("accountId").stringValue()).isEqualTo(destAccountId)
            assertThat(BigDecimal(finalDestAccount.get("balance").asText())).isEqualByComparingTo(expectedDestBalance)

            val sourceFinishedTxns =
                finalSourceAccount
                    .get("finishedTransactions")
                    ?.map { it.stringValue() }
                    ?.toSet() ?: emptySet()

            val destFinishedTxns =
                finalDestAccount
                    .get("finishedTransactions")
                    ?.map { it.stringValue() }
                    ?.toSet() ?: emptySet()

            assertThat(sourceFinishedTxns).contains(transactionId)
            assertThat(destFinishedTxns).contains(transactionId)
        }
    }

    /**
     * Asserts the TransactionFinished event was received, failing with rich diagnostics:
     * received event summary, current transaction state, and JetStream consumer health.
     * The guard lives here so the diagnostics builder (including the HTTP fetch) only
     * runs on the failure path. A plain [AssertionError] is thrown because Kotlin's
     * null-narrowing after the guard makes AssertJ's `assertThat` overload ambiguous.
     */
    private suspend fun assertFinishedEventPresent(
        finishedEvent: Message<*>?,
        messages: List<Message<*>>,
        transactionId: String,
    ) {
        if (finishedEvent != null) return
        throw AssertionError(buildFinishedEventFailureDiagnostics(messages, transactionId))
    }

    private suspend fun buildFinishedEventFailureDiagnostics(
        messages: List<Message<*>>,
        transactionId: String,
    ): String =
        buildString {
            append("Expected TransactionFinished event for transaction $transactionId. ")
            append(describeTransactionEventMessages(messages))
            append("; ")
            append(describeTransactionState(fetchTransactionState(transactionId)))
            append("; ")
            append(describeConsumerStates())
        }

    /**
     * Fetches the transaction's current state via HTTP for diagnostics.
     * Failures are swallowed (reported as unavailable): diagnostics must not mask the
     * original test failure. Cancellation is rethrown so coroutine cancellation
     * still propagates.
     */
    private suspend fun fetchTransactionState(transactionId: String): JsonNode? =
        try {
            transactionServiceWebClient
                .get()
                .uri("/api/v1/transactions/{transactionId}", transactionId)
                .retrieve()
                .awaitBody<JsonNode>()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }

    private fun describeConsumerStates(): String =
        describeConsumerRunningStates(
            listOfNotNull(
                bankAccountEventsNatsConsumer,
                bankTransactionEventsNatsConsumer,
            ).map { ConsumerRunningState(consumerName = it.consumerName, isRunning = it.isRunning()) },
        )

    /**
     * Polls for account state until both accounts have the finished transaction.
     * This handles the eventual consistency of the distributed system.
     */
    private suspend fun pollForAccountsWithFinishedTransaction(
        sourceAccountId: String,
        destAccountId: String,
        transactionId: String,
        maxRetries: Int,
        delayMillis: Long,
    ): Pair<JsonNode, JsonNode> {
        repeat(maxRetries) { attempt ->
            val sourceAccount: JsonNode =
                accountServiceWebClient
                    .get()
                    .uri("/api/v1/accounts/{accountId}", sourceAccountId)
                    .retrieve()
                    .awaitBody()

            val destAccount: JsonNode =
                accountServiceWebClient
                    .get()
                    .uri("/api/v1/accounts/{accountId}", destAccountId)
                    .retrieve()
                    .awaitBody()

            val sourceFinishedTxns =
                sourceAccount
                    .get("finishedTransactions")
                    ?.map { it.stringValue() }
                    ?.toSet() ?: emptySet()

            val destFinishedTxns =
                destAccount
                    .get("finishedTransactions")
                    ?.map { it.stringValue() }
                    ?.toSet() ?: emptySet()

            if (sourceFinishedTxns.contains(transactionId) && destFinishedTxns.contains(transactionId)) {
                return Pair(sourceAccount, destAccount)
            }

            if (attempt < maxRetries - 1) {
                kotlinx.coroutines.delay(delayMillis)
            }
        }

        throw IllegalStateException(
            "Timeout waiting for accounts to have finished transaction $transactionId",
        )
    }
}
