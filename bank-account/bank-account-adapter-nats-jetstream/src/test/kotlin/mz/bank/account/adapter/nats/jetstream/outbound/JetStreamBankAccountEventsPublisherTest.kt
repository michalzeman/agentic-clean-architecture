package mz.bank.account.adapter.nats.jetstream.outbound

import kotlinx.coroutines.runBlocking
import mz.bank.account.adapter.nats.jetstream.BankAccountJetStreamProperties
import mz.bank.account.adapter.nats.jetstream.mapper.BankAccountEventToJetStreamMapper
import mz.bank.account.contract.proto.BankAccountEvent
import mz.bank.account.domain.Email
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
import java.math.BigDecimal
import java.time.Instant
import mz.bank.account.domain.BankAccountEvent as DomainEvent

class JetStreamBankAccountEventsPublisherTest {
    private lateinit var jetStreamProducer: JetStreamProducer<BankAccountEvent>
    private lateinit var mapper: BankAccountEventToJetStreamMapper
    private lateinit var properties: BankAccountJetStreamProperties
    private lateinit var publisher: JetStreamBankAccountEventsPublisher

    @BeforeEach
    fun setUp() {
        jetStreamProducer = mock()
        mapper = BankAccountEventToJetStreamMapper()
        properties =
            BankAccountJetStreamProperties(
                bankAccountEventsStream = "bank-account.event.bank-accounts",
                bankAccountEventsSubject = "bank-account.event.bank-accounts.sub",
                bankTransactionEventsStream = "bank-transaction.event.bank-transactions",
                bankTransactionEventsSubject = "bank-transaction.event.bank-transactions.sub",
                consumerName = "bank-account-consumer",
                consumerGroup = "bank-account-service",
            )
        publisher = JetStreamBankAccountEventsPublisher(jetStreamProducer, mapper, properties)
    }

    @Test
    fun `should publish AccountCreated event to JetStream`() {
        // Given
        val domainEvent =
            DomainEvent.AccountCreated(
                aggregateId = AggregateId("acc-123"),
                updatedAt = Instant.parse("2025-01-15T10:30:00Z"),
                email = Email("test@example.com"),
                initialBalance = BigDecimal("1000.00"),
            )

        val publishAck =
            PublishAck(
                streamName = "bank-account.event.bank-accounts",
                sequenceNumber = 1L,
            )

        wheneverBlocking { jetStreamProducer.publish(any<JetStreamPublishRequest<BankAccountEvent>>()) }.thenReturn(publishAck)

        // When
        runBlocking {
            publisher.publish(domainEvent)

            // Then
            verify(jetStreamProducer).publish(any<JetStreamPublishRequest<BankAccountEvent>>())
        }
    }
}
