package mz.bank.account.adapter.nats.jetstream.config

import kotlinx.coroutines.runBlocking
import mz.bank.account.adapter.nats.jetstream.BankAccountJetStreamProperties
import mz.bank.account.adapter.nats.jetstream.mapper.BankAccountEventToJetStreamMapper
import mz.bank.account.domain.BankAccountEvent
import mz.shared.connector.nats.jetstream.producer.JetStreamProducer
import mz.shared.connector.nats.jetstream.producer.JetStreamPublishRequest
import mz.shared.connector.nats.jetstream.producer.PublishAck
import mz.shared.domain.AggregateId
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.wheneverBlocking
import java.time.Instant
import mz.bank.account.contract.proto.BankAccountEvent as ProtoBankAccountEvent

class OutboundJetStreamConfigurationTest {
    private lateinit var jetStreamProducer: JetStreamProducer<ProtoBankAccountEvent>
    private lateinit var publisher: mz.bank.account.adapter.nats.jetstream.outbound.JetStreamBankAccountEventsPublisher

    @BeforeEach
    fun setUp() {
        jetStreamProducer = mock()
        val mapper = BankAccountEventToJetStreamMapper()
        val properties =
            BankAccountJetStreamProperties(
                bankAccountEventsStream = "bank-account.event.bank-accounts",
                bankAccountEventsSubject = "bank-account.event.bank-accounts.sub",
                bankTransactionEventsStream = "bank-transaction.event.bank-transactions",
                bankTransactionEventsSubject = "bank-transaction.event.bank-transactions.sub",
                consumerName = "bank-account-consumer",
                consumerGroup = "bank-account-service",
            )
        publisher =
            mz.bank.account.adapter.nats.jetstream.outbound
                .JetStreamBankAccountEventsPublisher(jetStreamProducer, mapper, properties)
    }

    @Test
    fun `should delegate event to publisher`() {
        val event =
            BankAccountEvent.TransactionFinished(
                aggregateId = AggregateId("acc-123"),
                updatedAt = Instant.parse("2025-01-15T15:00:00Z"),
                transactionId = "txn-001",
            )

        wheneverBlocking { jetStreamProducer.publish(any<JetStreamPublishRequest<ProtoBankAccountEvent>>()) }.thenReturn(
            PublishAck(streamName = "test", sequenceNumber = 1L),
        )

        runBlocking { publisher.publish(event) }

        runBlocking { verify(jetStreamProducer).publish(any<JetStreamPublishRequest<ProtoBankAccountEvent>>()) }
    }
}
