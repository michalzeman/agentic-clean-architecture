package mz.bank.transaction.adapter.postgresql

import mz.bank.transaction.domain.BankTransactionEvent
import mz.shared.domain.AGGREGATE_ID
import mz.shared.domain.AggregateId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class BankTransactionDomainEventListenerTest {
    private lateinit var bankTransactionDomainEventsChannel: MessageChannel
    private lateinit var bankTransactionDomainEventListener: BankTransactionDomainEventListener

    @BeforeEach
    fun setUp() {
        bankTransactionDomainEventsChannel = mock()
        bankTransactionDomainEventListener = BankTransactionDomainEventListener(bankTransactionDomainEventsChannel)
    }

    @Test
    fun `should send event to channel with correct payload and aggregateId header`() {
        // Given
        val aggregateId = AggregateId(UUID.randomUUID().toString())
        val now = Instant.now()
        val event =
            BankTransactionEvent.BankTransactionCreated(
                aggregateId = aggregateId,
                correlationId = "corr-001",
                updatedAt = now,
                fromAccountId = AggregateId("acc-from"),
                toAccountId = AggregateId("acc-to"),
                amount = BigDecimal("500.00"),
            )
        val messageCaptor = argumentCaptor<Message<*>>()

        // When
        bankTransactionDomainEventListener.handle(event)

        // Then
        verify(bankTransactionDomainEventsChannel).send(messageCaptor.capture())
        val sentMessage = messageCaptor.firstValue
        assertThat(sentMessage.payload).isEqualTo(event)
        assertThat(sentMessage.headers[AGGREGATE_ID]).isEqualTo(aggregateId.value)
    }

    @Test
    fun `should send BankTransactionFinished event to channel with correct headers`() {
        // Given
        val aggregateId = AggregateId(UUID.randomUUID().toString())
        val now = Instant.now()
        val event =
            BankTransactionEvent.BankTransactionFinished(
                aggregateId = aggregateId,
                correlationId = "corr-finished",
                updatedAt = now,
                fromAccountId = AggregateId("acc-from"),
                toAccountId = AggregateId("acc-to"),
            )
        val messageCaptor = argumentCaptor<Message<*>>()

        // When
        bankTransactionDomainEventListener.handle(event)

        // Then
        verify(bankTransactionDomainEventsChannel).send(messageCaptor.capture())
        val sentMessage = messageCaptor.firstValue
        assertThat(sentMessage.payload).isInstanceOf(BankTransactionEvent.BankTransactionFinished::class.java)
        assertThat(sentMessage.headers[AGGREGATE_ID]).isEqualTo(aggregateId.value)
    }
}
