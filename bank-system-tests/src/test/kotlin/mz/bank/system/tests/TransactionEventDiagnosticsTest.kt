package mz.bank.system.tests

import mz.bank.transaction.contract.proto.BankTransactionEvent
import mz.bank.transaction.contract.proto.TransactionCreated
import mz.bank.transaction.contract.proto.TransactionDepositRolledBack
import mz.bank.transaction.contract.proto.TransactionFailed
import mz.bank.transaction.contract.proto.TransactionFinished
import mz.bank.transaction.contract.proto.TransactionMoneyDeposited
import mz.bank.transaction.contract.proto.TransactionMoneyWithdrawn
import mz.bank.transaction.contract.proto.TransactionRolledBack
import mz.bank.transaction.contract.proto.TransactionWithdrawRolledBack
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.messaging.Message
import org.springframework.messaging.support.MessageBuilder
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper

class TransactionEventDiagnosticsTest {
    private val objectMapper = ObjectMapper()

    @Test
    fun `should describe received message count and event discriminators`() {
        val messages =
            listOf(
                messageWith(
                    BankTransactionEvent
                        .newBuilder()
                        .setTransactionCreated(TransactionCreated.newBuilder().setAggregateId("tx-1").build())
                        .build(),
                ),
                messageWith(
                    BankTransactionEvent
                        .newBuilder()
                        .setTransactionMoneyWithdrawn(TransactionMoneyWithdrawn.newBuilder().setAggregateId("tx-1").build())
                        .build(),
                ),
                messageWith(
                    BankTransactionEvent
                        .newBuilder()
                        .setTransactionFinished(TransactionFinished.newBuilder().setAggregateId("tx-1").build())
                        .build(),
                ),
            )

        val description = describeTransactionEventMessages(messages)

        assertThat(description)
            .contains("3 message(s)")
            .contains("3 parsed as BankTransactionEvent")
            .contains("transactionCreated")
            .contains("transactionMoneyWithdrawn")
            .contains("transactionFinished")
    }

    @Test
    fun `should describe empty channel state when no messages received`() {
        val description = describeTransactionEventMessages(emptyList())

        assertThat(description)
            .contains("0 message(s)")
            .contains("event discriminators: []")
    }

    @Test
    fun `should count payloads that are not transaction events`() {
        val messages =
            listOf(
                messageWith(
                    BankTransactionEvent
                        .newBuilder()
                        .setTransactionCreated(TransactionCreated.newBuilder().setAggregateId("tx-1").build())
                        .build(),
                ),
                messageWith("not-a-transaction-event"),
            )

        val description = describeTransactionEventMessages(messages)

        assertThat(description)
            .contains("2 message(s)")
            .contains("1 parsed as BankTransactionEvent")
            .contains("unparsed payloads: 1")
    }

    @Test
    fun `should discriminate every transaction event oneof case`() {
        val cases =
            listOf(
                BankTransactionEvent
                    .newBuilder()
                    .setTransactionMoneyDeposited(TransactionMoneyDeposited.newBuilder().setAggregateId("tx-1").build())
                    .build(),
                BankTransactionEvent
                    .newBuilder()
                    .setTransactionFailed(TransactionFailed.newBuilder().setAggregateId("tx-1").build())
                    .build(),
                BankTransactionEvent
                    .newBuilder()
                    .setTransactionRolledBack(TransactionRolledBack.newBuilder().setAggregateId("tx-1").build())
                    .build(),
                BankTransactionEvent
                    .newBuilder()
                    .setTransactionWithdrawRolledBack(
                        TransactionWithdrawRolledBack.newBuilder().setAggregateId("tx-1").build(),
                    ).build(),
                BankTransactionEvent
                    .newBuilder()
                    .setTransactionDepositRolledBack(
                        TransactionDepositRolledBack.newBuilder().setAggregateId("tx-1").build(),
                    ).build(),
            )

        val discriminators = cases.map { it.eventCaseName() }

        assertThat(discriminators).containsExactly(
            "transactionMoneyDeposited",
            "transactionFailed",
            "transactionRolledBack",
            "transactionWithdrawRolledBack",
            "transactionDepositRolledBack",
        )
    }

    @Test
    fun `should describe transaction state from json`() {
        val state: JsonNode =
            objectMapper.readTree("""{"status":"FINISHED","moneyWithdrawn":true,"moneyDeposited":false}""")

        val description = describeTransactionState(state)

        assertThat(description)
            .contains("status=FINISHED")
            .contains("moneyWithdrawn=true")
            .contains("moneyDeposited=false")
    }

    @Test
    fun `should report missing for absent fields in transaction state`() {
        val state: JsonNode = objectMapper.readTree("""{"moneyWithdrawn":true}""")

        val description = describeTransactionState(state)

        assertThat(description)
            .contains("status=<missing>")
            .contains("moneyWithdrawn=true")
    }

    @Test
    fun `should report unavailable when transaction state is missing`() {
        assertThat(describeTransactionState(null)).contains("unavailable")
    }

    @Test
    fun `should describe consumer running states`() {
        val description =
            describeConsumerRunningStates(
                listOf(
                    ConsumerRunningState(consumerName = "bank-account-consumer", isRunning = true),
                    ConsumerRunningState(consumerName = "bank-transaction-consumer", isRunning = false),
                ),
            )

        assertThat(description)
            .contains("bank-account-consumer: isRunning=true")
            .contains("bank-transaction-consumer: isRunning=false")
    }

    @Test
    fun `should report missing consumers when none are present`() {
        assertThat(describeConsumerRunningStates(emptyList())).contains("no JetStream consumer beans")
    }

    private fun messageWith(payload: Any): Message<*> = MessageBuilder.withPayload(payload).build()
}
