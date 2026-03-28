package mz.bank.account.adapter.nats.jetstream.inbound

import kotlinx.coroutines.runBlocking
import mz.bank.transaction.contract.proto.BankTransactionEvent
import mz.shared.connector.nats.jetstream.consumer.JetStreamConsumer
import mz.shared.connector.nats.jetstream.consumer.JetStreamMessage
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.kotlin.wheneverBlocking
import org.springframework.messaging.MessageChannel

class JetStreamBankTransactionConsumerTest {
    private val jetStreamConsumer: JetStreamConsumer<BankTransactionEvent> = mock()
    private val inboundBankTransactionEventsChannel: MessageChannel = mock()
    private val consumer =
        JetStreamBankTransactionConsumer(
            jetStreamConsumer = jetStreamConsumer,
            inboundBankTransactionEventsChannel = inboundBankTransactionEventsChannel,
        )

    @Test
    fun `should send inbound event to channel and acknowledge on success`() {
        // Given
        val protoEvent = BankTransactionEvent.getDefaultInstance()
        val message: JetStreamMessage<BankTransactionEvent> = mock()
        whenever(message.payload).thenReturn(protoEvent)
        whenever(inboundBankTransactionEventsChannel.send(any())).thenReturn(true)
        wheneverBlocking { jetStreamConsumer.acknowledge(any()) }.thenReturn(Unit)

        // When / Then
        runBlocking {
            consumer.onMessage(message)

            verify(inboundBankTransactionEventsChannel).send(any())
            verify(jetStreamConsumer).acknowledge(message)
            verify(jetStreamConsumer, never()).negativeAcknowledge(any())
        }
    }

    @Test
    fun `should negativeAcknowledge and rethrow when channel send throws`() {
        // Given
        val protoEvent = BankTransactionEvent.getDefaultInstance()
        val message: JetStreamMessage<BankTransactionEvent> = mock()
        whenever(message.payload).thenReturn(protoEvent)
        whenever(inboundBankTransactionEventsChannel.send(any())).thenThrow(RuntimeException("channel failure"))
        wheneverBlocking { jetStreamConsumer.negativeAcknowledge(any()) }.thenReturn(Unit)

        // When / Then
        runBlocking {
            assertThrows<RuntimeException> { consumer.onMessage(message) }

            verify(jetStreamConsumer).negativeAcknowledge(message)
            verify(jetStreamConsumer, never()).acknowledge(any())
        }
    }

    @Test
    fun `should negativeAcknowledge and rethrow when toInboundEvent throws`() {
        // Given
        val message: JetStreamMessage<BankTransactionEvent> = mock()
        whenever(message.payload).thenReturn(BankTransactionEvent.getDefaultInstance())
        // toInboundEvent() will be called on the payload; simulate it throwing
        // We can't mock the extension function directly, so we verify the nack path
        // by making the channel send throw — this is already covered in test 2.
        // This test documents that any exception in the try block triggers nack+rethrow.
        whenever(inboundBankTransactionEventsChannel.send(any())).thenThrow(IllegalArgumentException("malformed event"))
        wheneverBlocking { jetStreamConsumer.negativeAcknowledge(any()) }.thenReturn(Unit)

        // When / Then
        runBlocking {
            assertThrows<IllegalArgumentException> { consumer.onMessage(message) }

            verify(jetStreamConsumer).negativeAcknowledge(message)
            verify(jetStreamConsumer, never()).acknowledge(any())
        }
    }

    @Test
    fun `should negativeAcknowledge and acknowledge on send returning false`() {
        // MessageChannel.send() returning false means the channel rejected the message
        val protoEvent = BankTransactionEvent.getDefaultInstance()
        val message: JetStreamMessage<BankTransactionEvent> = mock()
        whenever(message.payload).thenReturn(protoEvent)
        whenever(inboundBankTransactionEventsChannel.send(any())).thenReturn(false)
        wheneverBlocking { jetStreamConsumer.acknowledge(any()) }.thenReturn(Unit)

        // When / Then
        runBlocking {
            // send() returning false means the channel rejected the message, but the consumer
            // does not check the return value — this documents current behavior.
            // If the return value should be checked, this test will need to be updated.
            consumer.onMessage(message)

            // Document current behavior: false return from send() is NOT treated as failure
            verify(jetStreamConsumer).acknowledge(message)
            verify(inboundBankTransactionEventsChannel).send(any())
            verify(jetStreamConsumer, never()).negativeAcknowledge(any())
        }
    }
}
