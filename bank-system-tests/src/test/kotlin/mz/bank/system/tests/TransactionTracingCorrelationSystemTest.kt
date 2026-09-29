package mz.bank.system.tests

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import mz.bank.account.contract.proto.BankAccountEvent
import mz.bank.system.tests.wiring.BankSystemTestConfiguration
import mz.bank.transaction.contract.proto.BankTransactionEvent
import mz.shared.connector.system.tests.awaitMessages
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.integration.channel.QueueChannel
import org.springframework.messaging.Message
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.awaitBody
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Acceptance system test for cross-service trace correlation (spec Step 6).
 *
 * Runs one transfer saga through the running stack, then reads both services'
 * container stdout (`docker compose logs`) and asserts:
 * 1. The same `traceId` appears in bank-transaction and bank-account log lines
 *    that mention the saga.
 * 2. A log line exists per hop (withdraw, deposit, finish; compensation when
 *    the cancellation method runs).
 *
 * Not gated on NATS_JETSTREAM_ENABLED: the saga is driven over REST and the
 * assertions read docker logs, so the transport in between is irrelevant and
 * both Redis Streams and JetStream variants are covered by the same run.
 * Channel beans are transport-conditional, so the channels are resolved
 * through [applicationContext] at runtime instead of a fixed qualifier.
 */
@Tag("systemChecks")
@SpringBootTest(
    classes = [BankSystemTestConfiguration::class],
)
@TestInstance(Lifecycle.PER_CLASS)
class TransactionTracingCorrelationSystemTest {
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
    private lateinit var applicationContext: ApplicationContext

    /**
     * `BankSystemTestConfiguration` swaps channel bean names between the Redis
     * Streams and JetStream variants; a non-gated test resolves whoever exists.
     */
    private val bankAccountEventsChannel: QueueChannel by lazy {
        applicationContext.getBean(
            pickChannelBean("bankAccountEventsStreamChannel", "bankAccountEventsNatsStreamChannel"),
            QueueChannel::class.java,
        )
    }

    private val bankTransactionEventsChannel: QueueChannel by lazy {
        applicationContext.getBean(
            pickChannelBean("bankTransactionEventsStreamChannel", "bankTransactionEventsNatsStreamChannel"),
            QueueChannel::class.java,
        )
    }

    private fun pickChannelBean(
        redisBeanName: String,
        natsBeanName: String,
    ): String = if (applicationContext.containsBean(natsBeanName)) natsBeanName else redisBeanName

    @Test
    fun `should log the same traceId in both services for one transfer saga`() {
        runBlocking {
            awaitServicesReady()

            val sourceAccountId = createAccount("trace-source")
            val destAccountId = createAccount("trace-dest")
            awaitAccountCreatedEvents(sourceAccountId, destAccountId)

            val transaction: JsonNode = createTransaction(sourceAccountId, destAccountId)
            val transactionId = transaction.get("transactionId").stringValue()
            assertThat(transactionId).isNotBlank

            awaitTransactionStatus(transactionId, expectedStatus = "FINISHED", maxRetries = 90, delayMillis = 2000)

            val sagaAccountIds = listOf(sourceAccountId, destAccountId)
            // The FINISHED REST status precedes the final async round-trip (the
            // accounts ship Transaction Finished events back to the transaction
            // service, which logs the finish hop), so await for that hop line too.
            val transactionServiceLines =
                awaitLogLines("bank-transaction-service") { lines ->
                    lines.any { it.message.contains("transactionId=$transactionId") && it.traceId != null } &&
                        lines.any { it.message.contains("Transaction finished for transactionId=$transactionId") }
                }
            val accountServiceLines =
                awaitLogLines("bank-account-service") { lines ->
                    lines.any { it.message.contains("[Emission] - eventType=TransferWithdrawalStarted, aggregateId=$sourceAccountId") } &&
                        lines.any { it.message.contains("[Emission] - eventType=TransferDepositStarted, aggregateId=$destAccountId") } &&
                        lines.any { it.message.contains("[Emission] - eventType=TransactionFinished") } &&
                        sagaAccountIds.any { id ->
                            lines.any { it.message.contains("[Emission] - eventType=TransactionFinished, aggregateId=$id") }
                        }
                }

            // 1. Trace correlation: the account-side hop emission lines must carry
            //    traceIds from the transaction service's saga trace, not just any
            //    intersection (account-creation traces could otherwise satisfy it).
            val sagaTraceIds =
                transactionServiceLines
                    .filter { it.message.contains("transactionId=$transactionId") }
                    .mapNotNull { it.traceId }
                    .toSet()
            val hopEmissionLines =
                accountServiceLines.filter { line ->
                    line.message.contains("[Emission] - eventType=TransferWithdrawalStarted, aggregateId=$sourceAccountId") ||
                        line.message.contains("[Emission] - eventType=TransferDepositStarted, aggregateId=$destAccountId") ||
                        (
                            line.message.contains("[Emission] - eventType=TransactionFinished") &&
                                sagaAccountIds.any { id -> line.message.contains("aggregateId=$id") }
                        )
                }

            assertThat(hopEmissionLines).isNotEmpty
            val linesWithoutTraceId =
                hopEmissionLines.filter { it.traceId == null }
            val subsetFailureMessage =
                "saga hop emission traceIds for transactionId=$transactionId must be in the transaction service's saga traceIds\n" +
                    "=== bank-transaction-service saga lines ===\n${transactionServiceLines.renderDiagnostic()}\n" +
                    "=== bank-account-service saga lines ===\n${accountServiceLines.renderDiagnostic()}"
            assertThat(linesWithoutTraceId)
                .`as`(
                    "every saga hop emission line for transactionId=$transactionId must carry a traceId\n" +
                        "=== missing-trace hop lines ===\n${linesWithoutTraceId.renderDiagnostic()}",
                ).isEmpty()
            assertThat(hopEmissionLines.mapNotNull { it.traceId })
                .`as`(subsetFailureMessage)
                .isSubsetOf(sagaTraceIds)

            // 2. Per-hop log lines in bank-transaction: withdraw, deposit, finish.
            assertThat(transactionServiceLines.map { it.message })
                .`as`("bank-transaction-service per-hop lines for transactionId=$transactionId")
                .anyMatch { it.contains("Transfer withdrawal started for transactionId=$transactionId") }
                .anyMatch { it.contains("Transfer deposit started for transactionId=$transactionId") }
                .anyMatch { it.contains("Transaction finished for transactionId=$transactionId") }

            // 3. Per-hop emission lines in bank-account: the withdrawal hop on the
            //    source account, the deposit hop on the destination account, and the
            //    finish event (emitted per account).
            assertThat(accountServiceLines.map { it.message })
                .`as`("bank-account-service emission lines for transactionId=$transactionId")
                .anyMatch { it.contains("[Emission] - eventType=TransferWithdrawalStarted, aggregateId=$sourceAccountId") }
                .anyMatch { it.contains("[Emission] - eventType=TransferDepositStarted, aggregateId=$destAccountId") }
                .anyMatch { line ->
                    line.contains("[Emission] - eventType=TransactionFinished, aggregateId=$sourceAccountId") ||
                        line.contains("[Emission] - eventType=TransactionFinished, aggregateId=$destAccountId")
                }

            // Logging-only diagnostic on success path is unnecessary; failures above
            // print the collected lines via renderDiagnostic().
        }
    }

    @Test
    fun `should log compensation hop lines when a transfer is cancelled`() {
        runBlocking {
            awaitServicesReady()

            val sourceAccountId = createAccount("cancel-trace-source")
            val destAccountId = createAccount("cancel-trace-dest")
            awaitAccountCreatedEvents(sourceAccountId, destAccountId)

            val transferAmount = BigDecimal("800.00")
            val transaction: JsonNode = createTransaction(sourceAccountId, destAccountId, transferAmount)
            val transactionId = transaction.get("transactionId").stringValue()
            assertThat(transactionId).isNotBlank

            // Wait until money is withdrawn, then cancel before the saga finishes.
            awaitTransactionState(transactionId, maxRetries = 60, delayMillis = 500) { state ->
                state.get("moneyWithdrawn").asBoolean()
            }

            val cancelledTransaction: JsonNode =
                cancelTransaction(
                    transactionId = transactionId,
                    fromAccountId = sourceAccountId,
                    toAccountId = destAccountId,
                    amount = transferAmount,
                )
            assertThat(cancelledTransaction.get("transactionId").stringValue()).isEqualTo(transactionId)
            assertThat(cancelledTransaction.get("status").stringValue()).isEqualTo("FAILED")

            // Await the rolled-back event so the compensation chain has completed
            // before reading either service's logs.
            bankTransactionEventsChannel.awaitMessages(
                maxDelayMillis = 60000,
                pollIntervalMillis = 500,
                assertion = { msgs: List<Message<*>> ->
                    msgs.any { msg: Message<*> ->
                        val payload = msg.payload as? BankTransactionEvent
                        payload?.hasTransactionRolledBack() == true &&
                            payload.transactionRolledBack.aggregateId == transactionId
                    }
                },
            )
            awaitTransactionStatus(transactionId, expectedStatus = "FAILED", maxRetries = 30, delayMillis = 1000)

            val transactionServiceLines =
                awaitLogLines("bank-transaction-service") { lines ->
                    lines.any { it.message.contains("Transfer withdrawal rolled back for transactionId=$transactionId") }
                }
            val accountServiceLines =
                awaitLogLines("bank-account-service") { lines ->
                    lines.any {
                        it.message.contains("[Emission] - eventType=TransferWithdrawalRolledBack, aggregateId=$sourceAccountId")
                    }
                }

            // Trace correlation for the compensation path: the account-side
            // rollback emission lines must carry traceIds from the transaction
            // service's cancellation trace, not just any intersection.
            val sagaTraceIds =
                transactionServiceLines
                    .filter { it.message.contains("transactionId=$transactionId") }
                    .mapNotNull { it.traceId }
                    .toSet()
            val rollbackEmissionLines =
                accountServiceLines.filter { line ->
                    line.message.contains("[Emission] - eventType=TransferWithdrawalRolledBack, aggregateId=$sourceAccountId")
                }

            assertThat(rollbackEmissionLines).isNotEmpty
            val linesWithoutTraceId =
                rollbackEmissionLines.filter { it.traceId == null }
            assertThat(linesWithoutTraceId)
                .`as`(
                    "every rollback emission line for transactionId=$transactionId must carry a traceId\n" +
                        "=== missing-trace rollback lines ===\n${linesWithoutTraceId.renderDiagnostic()}",
                ).isEmpty()
            val rollbackFailureMessage =
                "rollback emission traceIds for transactionId=$transactionId must be in the " +
                    "transaction service's cancellation traceIds\n" +
                    "=== bank-transaction-service saga lines ===\n${transactionServiceLines.renderDiagnostic()}\n" +
                    "=== bank-account-service saga lines ===\n${accountServiceLines.renderDiagnostic()}"
            assertThat(rollbackEmissionLines.mapNotNull { it.traceId })
                .`as`(rollbackFailureMessage)
                .isSubsetOf(sagaTraceIds)

            assertThat(transactionServiceLines.map { it.message })
                .`as`("bank-transaction-service compensation lines for transactionId=$transactionId")
                .anyMatch { it.contains("Transfer withdrawal rolled back for transactionId=$transactionId") }

            assertThat(accountServiceLines.map { it.message })
                .`as`("bank-account-service compensation emission lines for transactionId=$transactionId")
                .anyMatch { it.contains("[Emission] - eventType=TransferWithdrawalRolledBack, aggregateId=$sourceAccountId") }
        }
    }

    // ─── saga helpers ─────────────────────────────────────────────────────────

    @BeforeAll
    fun setUp() {
        runBlocking {
            listOf(
                accountServiceHealthChecker,
                transactionServiceHealthChecker,
            ).waitForAllServices()
        }
    }

    private suspend fun awaitServicesReady() {
        listOf(
            accountServiceHealthChecker,
            transactionServiceHealthChecker,
        ).waitForAllServices()
    }

    private suspend fun createAccount(emailPrefix: String): String {
        val testId = UUID.randomUUID().toString()
        val request =
            objectMapper.buildAccountRequest(
                "$emailPrefix-$testId-${Instant.now().epochSecond}@example.com",
                BigDecimal("5000.00"),
            )
        val account: JsonNode =
            accountServiceWebClient
                .post()
                .uri("/api/v1/accounts")
                .header("Content-Type", "application/json")
                .bodyValue(request)
                .retrieve()
                .awaitBody()
        val accountId = account.get("accountId").stringValue()
        assertThat(accountId).isNotBlank
        return accountId
    }

    private suspend fun awaitAccountCreatedEvents(
        sourceAccountId: String,
        destAccountId: String,
    ) {
        bankAccountEventsChannel.awaitMessages(
            maxDelayMillis = 30000,
            pollIntervalMillis = 500,
            assertion = { msgs: List<Message<*>> ->
                val createdAccountIds =
                    msgs.mapNotNull { msg: Message<*> ->
                        val payload = msg.payload as? BankAccountEvent
                        payload?.takeIf { it.hasAccountCreated() }?.accountCreated?.aggregateId
                    }
                createdAccountIds.contains(sourceAccountId) && createdAccountIds.contains(destAccountId)
            },
        )
    }

    private suspend fun createTransaction(
        fromAccountId: String,
        toAccountId: String,
        amount: BigDecimal = BigDecimal("500.00"),
    ): JsonNode =
        retryWithBackoff(
            maxRetries = 10,
            initialDelayMillis = 500,
        ) {
            transactionServiceWebClient
                .post()
                .uri("/api/v1/transactions")
                .header("Content-Type", "application/json")
                .bodyValue(objectMapper.buildTransactionRequest(fromAccountId, toAccountId, amount))
                .retrieve()
                .awaitBody()
        }

    /** Polls GET /api/v1/transactions/{id} until [predicate] holds on the response. */
    private suspend fun awaitTransactionState(
        transactionId: String,
        maxRetries: Int,
        delayMillis: Long,
        predicate: (JsonNode) -> Boolean,
    ): JsonNode {
        repeat(maxRetries) { attempt ->
            val transaction: JsonNode =
                transactionServiceWebClient
                    .get()
                    .uri("/api/v1/transactions/{transactionId}", transactionId)
                    .retrieve()
                    .awaitBody()
            if (predicate(transaction)) {
                return transaction
            }
            if (attempt < maxRetries - 1) {
                delay(delayMillis)
            }
        }
        throw IllegalStateException(
            "Timeout waiting for transaction $transactionId to satisfy the polled state predicate",
        )
    }

    private suspend fun awaitTransactionStatus(
        transactionId: String,
        expectedStatus: String,
        maxRetries: Int,
        delayMillis: Long,
    ): JsonNode =
        awaitTransactionState(transactionId, maxRetries, delayMillis) { transaction ->
            transaction.get("status").stringValue() == expectedStatus
        }

    /**
     * Cancels the transaction, retrying once on 503 (lock acquisition failure)
     * with a 2-second delay. Do NOT retry on 400 (already finished) — that
     * means the race was lost.
     */
    private suspend fun cancelTransaction(
        transactionId: String,
        fromAccountId: String,
        toAccountId: String,
        amount: BigDecimal,
    ): JsonNode {
        val request = buildCancelTransactionRequest(fromAccountId, toAccountId, amount)
        return try {
            postCancel(transactionId, request)
        } catch (e: WebClientResponseException.ServiceUnavailable) {
            kotlinx.coroutines.delay(2000)
            postCancel(transactionId, request)
        }
    }

    private suspend fun postCancel(
        transactionId: String,
        request: JsonNode,
    ): JsonNode =
        transactionServiceWebClient
            .post()
            .uri("/api/v1/transactions/{transactionId}/cancel", transactionId)
            .header("Content-Type", "application/json")
            .bodyValue(request)
            .retrieve()
            .awaitBody()

    private fun buildCancelTransactionRequest(
        fromAccountId: String,
        toAccountId: String,
        amount: BigDecimal,
    ): JsonNode =
        objectMapper.createObjectNode().apply {
            put("fromAccountId", fromAccountId)
            put("toAccountId", toAccountId)
            put("amount", amount)
        }
}
