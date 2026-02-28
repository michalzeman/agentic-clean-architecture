package mz.bank.account.adapter.postgresql

import mz.bank.account.domain.BankAccountEvent
import mz.bank.account.domain.Email
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

class BankAccountDomainEventListenerTest {
    private lateinit var bankAccountDomainEventsChannel: MessageChannel
    private lateinit var bankAccountDomainEventListener: BankAccountDomainEventListener

    @BeforeEach
    fun setUp() {
        bankAccountDomainEventsChannel = mock()
        bankAccountDomainEventListener = BankAccountDomainEventListener(bankAccountDomainEventsChannel)
    }

    @Test
    fun `should send event to channel with correct payload and aggregateId header`() {
        // Given
        val aggregateId = AggregateId(UUID.randomUUID().toString())
        val now = Instant.now()
        val event = BankAccountEvent.AccountCreated(aggregateId, now, Email("test@example.com"), BigDecimal("100.00"))
        val messageCaptor = argumentCaptor<Message<*>>()

        // When
        bankAccountDomainEventListener.handle(event)

        // Then
        verify(bankAccountDomainEventsChannel).send(messageCaptor.capture())
        val sentMessage = messageCaptor.firstValue
        assertThat(sentMessage.payload).isEqualTo(event)
        assertThat(sentMessage.headers[AGGREGATE_ID]).isEqualTo(aggregateId.value)
    }

    @Test
    fun `should send MoneyDeposited event to channel with correct headers`() {
        // Given
        val aggregateId = AggregateId(UUID.randomUUID().toString())
        val now = Instant.now()
        val event = BankAccountEvent.MoneyDeposited(aggregateId, now, BigDecimal("50.00"))
        val messageCaptor = argumentCaptor<Message<*>>()

        // When
        bankAccountDomainEventListener.handle(event)

        // Then
        verify(bankAccountDomainEventsChannel).send(messageCaptor.capture())
        val sentMessage = messageCaptor.firstValue
        assertThat(sentMessage.payload).isInstanceOf(BankAccountEvent.MoneyDeposited::class.java)
        assertThat(sentMessage.headers[AGGREGATE_ID]).isEqualTo(aggregateId.value)
    }
}
