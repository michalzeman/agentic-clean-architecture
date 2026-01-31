package mz.bank.system.tests

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.runBlocking
import mz.bank.system.tests.wiring.BankSystemTestConfiguration
import mz.shared.connector.system.tests.awaitMessages
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.integration.channel.QueueChannel
import org.springframework.messaging.Message
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.awaitBody
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * System integration test for bank transaction flow.
 * Tests the complete transaction lifecycle:
 * 1. Create source and destination accounts via HTTP API
 * 2. Create a transaction via HTTP API
 * 3. Consume bank-transaction events from queue channel
 * 4. Verify transaction finished by checking status
 * 5. Verify account balances via HTTP API
 */
@Tag("systemChecks")
@SpringBootTest(
    classes = [BankSystemTestConfiguration::class],
)
@TestInstance(Lifecycle.PER_CLASS)
class TransactionIntegrationSystemTest {
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
    @Qualifier("bankTransactionEventsStreamChannel")
    private lateinit var bankTransactionEventsChannel: QueueChannel

    @Autowired
    @Qualifier("bankAccountEventsStreamChannel")
    private lateinit var bankAccountEventsChannel: QueueChannel

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
    fun `should complete full transaction flow and verify account balances`() {
        runBlocking {
            val testId = UUID.randomUUID().toString()
            val timestamp = Instant.now().epochSecond

            val sourceAccountEmail = "source-$testId-$timestamp@example.com"
            val sourceInitialBalance = BigDecimal("5000.00")
            val sourceAccountRequest = buildAccountRequest(sourceAccountEmail, sourceInitialBalance)

            val sourceAccount: JsonNode =
                accountServiceWebClient
                    .post()
                    .uri("/api/v1/accounts")
                    .header("Content-Type", "application/json")
                    .bodyValue(sourceAccountRequest)
                    .retrieve()
                    .awaitBody()

            val sourceAccountId = sourceAccount.get("accountId").asText()
            assertThat(sourceAccountId).isNotNull
            assertThat(BigDecimal(sourceAccount.get("balance").asText())).isEqualByComparingTo(sourceInitialBalance)

            val destAccountEmail = "dest-$testId-$timestamp@example.com"
            val destInitialBalance = BigDecimal("1000.00")
            val destAccountRequest = buildAccountRequest(destAccountEmail, destInitialBalance)

            val destAccount: JsonNode =
                accountServiceWebClient
                    .post()
                    .uri("/api/v1/accounts")
                    .header("Content-Type", "application/json")
                    .bodyValue(destAccountRequest)
                    .retrieve()
                    .awaitBody()

            val destAccountId = destAccount.get("accountId").asText()
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
            val transactionRequest = buildTransactionRequest(sourceAccountId, destAccountId, transferAmount)

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

            val transactionId = transaction.get("transactionId").asText()
            assertThat(transactionId).isNotNull
            assertThat(transaction.get("correlationId").asText()).isNotNull
            assertThat(transaction.get("status").asText()).isEqualTo("CREATED")
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
            assertThat(finishedEvent)
                .withFailMessage("Expected TransactionFinished event for transaction $transactionId")
                .isNotNull

            val finalTransaction: JsonNode =
                transactionServiceWebClient
                    .get()
                    .uri("/api/v1/transactions/{transactionId}", transactionId)
                    .retrieve()
                    .awaitBody()

            assertThat(finalTransaction.get("transactionId").asText()).isEqualTo(transactionId)
            assertThat(finalTransaction.get("status").asText()).isEqualTo("FINISHED")
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
            assertThat(finalSourceAccount.get("accountId").asText()).isEqualTo(sourceAccountId)
            assertThat(BigDecimal(finalSourceAccount.get("balance").asText())).isEqualByComparingTo(expectedSourceBalance)

            val expectedDestBalance = destInitialBalance.add(transferAmount)
            assertThat(finalDestAccount.get("accountId").asText()).isEqualTo(destAccountId)
            assertThat(BigDecimal(finalDestAccount.get("balance").asText())).isEqualByComparingTo(expectedDestBalance)

            val sourceFinishedTxns =
                finalSourceAccount
                    .get("finishedTransactions")
                    ?.map { it.asText() }
                    ?.toSet() ?: emptySet()

            val destFinishedTxns =
                finalDestAccount
                    .get("finishedTransactions")
                    ?.map { it.asText() }
                    ?.toSet() ?: emptySet()

            assertThat(sourceFinishedTxns).contains(transactionId)
            assertThat(destFinishedTxns).contains(transactionId)
        }
    }

    private fun buildAccountRequest(
        email: String,
        initialBalance: BigDecimal,
    ): JsonNode {
        val node = objectMapper.createObjectNode()
        node.put("email", email)
        node.put("initialBalance", initialBalance)
        return node
    }

    private fun buildTransactionRequest(
        fromAccountId: String,
        toAccountId: String,
        amount: BigDecimal,
    ): JsonNode {
        val node = objectMapper.createObjectNode()
        node.put("fromAccountId", fromAccountId)
        node.put("toAccountId", toAccountId)
        node.put("amount", amount)
        return node
    }

    /**
     * Retries a suspend function with exponential backoff.
     * Used to handle race conditions in distributed system tests.
     */
    private suspend fun <T> retryWithBackoff(
        maxRetries: Int,
        initialDelayMillis: Long,
        block: suspend () -> T,
    ): T {
        var delayMillis = initialDelayMillis
        repeat(maxRetries) { attempt ->
            try {
                return block()
            } catch (e: WebClientResponseException) {
                if (attempt == maxRetries - 1) {
                    throw e
                }
                kotlinx.coroutines.delay(delayMillis)
                delayMillis *= 2
            }
        }
        throw IllegalStateException("Retry failed")
    }

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
                    ?.map { it.asText() }
                    ?.toSet() ?: emptySet()

            val destFinishedTxns =
                destAccount
                    .get("finishedTransactions")
                    ?.map { it.asText() }
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
