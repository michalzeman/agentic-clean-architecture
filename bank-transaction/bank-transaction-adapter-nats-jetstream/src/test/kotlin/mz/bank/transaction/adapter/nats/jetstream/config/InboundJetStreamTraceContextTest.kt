package mz.bank.transaction.adapter.nats.jetstream.config

import io.micrometer.tracing.Span
import io.micrometer.tracing.test.simple.SimpleTracer
import io.nats.client.Connection
import io.nats.client.JetStream
import io.nats.client.JetStreamSubscription
import io.nats.client.PullSubscribeOptions
import io.nats.client.impl.Headers
import io.nats.client.impl.NatsJetStreamMetaData
import mz.bank.account.contract.proto.BankAccountEvent
import mz.bank.account.contract.proto.TransferWithdrawalStarted
import mz.bank.transaction.adapter.nats.jetstream.BankTransactionJetStreamProperties
import mz.bank.transaction.application.account.AccountEvent
import mz.shared.connector.nats.NatsConnectionFactory
import mz.shared.connector.nats.jetstream.consumer.JetStreamMessage
import mz.shared.connector.nats.jetstream.message.JetStreamMessageConverter
import mz.shared.connector.observability.SimpleW3CPropagator
import mz.shared.connector.observability.TraceParent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import io.nats.client.Message as NatsMessage

private const val TRACE_ID = "0af7651916cd43dd8448eb211c80319c"
private const val TRACEPARENT_VALUE = "00-$TRACE_ID-b7ad6b7169203331-01"
private const val CONSUMER_NAME = "bank-transaction-consumer"

/**
 * Inbound JetStream hop of the W3C `traceparent` propagation in the bank-transaction
 * service: the handler must carry the header onto the Spring Integration message handed
 * to the inbound channel (the channel never moves ThreadLocals — the header is the
 * propagation contract across the JDBC-backed queue poller hops), and the consumer must
 * restore the span from the header so the whole handler runs inside the propagated trace.
 */
class InboundJetStreamTraceContextTest {
    private val tracer = SimpleTracer()
    private val traceParent = TraceParent(tracer, SimpleW3CPropagator(tracer))

    @AfterEach
    fun clearCurrentSpan() {
        tracer.withSpan(null as Span?)
    }

    @Test
    fun `handler copies the traceparent header onto the message sent to the inbound channel`() {
        val sent = CopyOnWriteArrayList<Message<AccountEvent>>()
        val channel = channelCapturingSentMessages(sent)

        configuration().sendToInboundChannel(jetStreamMessage(traceparent = TRACEPARENT_VALUE), channel)

        assertThat(sent).hasSize(1)
        assertThat(sent[0].headers[TraceParent.HEADER]).isEqualTo(TRACEPARENT_VALUE)
        assertThat(sent[0].payload).isInstanceOf(AccountEvent.TransferWithdrawalStartedEvent::class.java)
    }

    @Test
    fun `handler does not copy NATS internal headers onto the message sent to the inbound channel`() {
        val sent = CopyOnWriteArrayList<Message<AccountEvent>>()
        val channel = channelCapturingSentMessages(sent)
        val jetStreamMessage =
            jetStreamMessage(
                traceparent = TRACEPARENT_VALUE,
                extraHeaders = mapOf("Content-Type" to listOf("application/protobuf"), "Nats-Msg-Id" to listOf("nats-1")),
            )

        configuration().sendToInboundChannel(jetStreamMessage, channel)

        assertThat(sent[0].headers).doesNotContainKeys("Content-Type", "Nats-Msg-Id")
    }

    @Test
    fun `handler sends a message without a traceparent header when the jetstream message carries none`() {
        val sent = CopyOnWriteArrayList<Message<AccountEvent>>()
        val channel = channelCapturingSentMessages(sent)

        configuration().sendToInboundChannel(jetStreamMessage(traceparent = null), channel)

        assertThat(sent[0].headers).doesNotContainKey(TraceParent.HEADER)
    }

    @Test
    fun `consumer restores the traceparent span context while the handler sends to the channel`() {
        val observedTraceIds = CopyOnWriteArrayList<String?>()
        val channelSent = CountDownLatch(1)

        val consumer =
            configuration().jetStreamConsumer(
                connectionFactory = mockedNatsTransport(natsMessage(traceparent = TRACEPARENT_VALUE)),
                bankAccountEventJetStreamConverter = converterReturningProtoEvent(),
                inboundBankAccountEventsChannel = channelObservingTraceIds(observedTraceIds, channelSent),
                traceParent = traceParent,
            )

        consumer.start()
        assertThat(channelSent.await(5, TimeUnit.SECONDS)).isTrue()
        consumer.stop()

        assertThat(observedTraceIds).containsExactly(TRACE_ID)
    }

    @Test
    fun `consumer handles a message without a traceparent header with a fresh trace`() {
        val observedTraceIds = CopyOnWriteArrayList<String?>()
        val channelSent = CountDownLatch(1)

        val consumer =
            configuration().jetStreamConsumer(
                connectionFactory = mockedNatsTransport(natsMessage(traceparent = null)),
                bankAccountEventJetStreamConverter = converterReturningProtoEvent(),
                inboundBankAccountEventsChannel = channelObservingTraceIds(observedTraceIds, channelSent),
                traceParent = traceParent,
            )

        consumer.start()
        assertThat(channelSent.await(5, TimeUnit.SECONDS)).isTrue()
        consumer.stop()

        assertThat(observedTraceIds).containsExactly(null as String?)
    }

    private fun configuration(): InboundJetStreamConfiguration =
        InboundJetStreamConfiguration(
            BankTransactionJetStreamProperties(
                bankTransactionEventsStream = "bank-transaction.event.bank-transactions",
                bankTransactionEventsSubject = "bank-transaction.event.bank-transactions.sub",
                bankAccountEventsStream = "bank-account.event.bank-accounts",
                bankAccountEventsSubject = "bank-account.event.bank-accounts.sub",
                consumerName = CONSUMER_NAME,
                consumerGroup = "bank-transaction-service",
            ),
        )

    private fun mockedNatsTransport(delivered: NatsMessage): NatsConnectionFactory {
        val subscription = mock<JetStreamSubscription>()
        whenever(subscription.isActive).thenReturn(true)
        whenever(subscription.fetch(any<Int>(), any<Duration>())).thenReturn(listOf(delivered), emptyList())
        val jetStream = mock<JetStream>()
        whenever(jetStream.subscribe(any(), any<PullSubscribeOptions>())).thenReturn(subscription)
        val connection = mock<Connection>()
        whenever(connection.status).thenReturn(Connection.Status.CONNECTED)
        whenever(connection.jetStream()).thenReturn(jetStream)
        val factory = mock<NatsConnectionFactory>()
        whenever(factory.getConnection()).thenReturn(connection)
        return factory
    }

    private fun natsMessage(traceparent: String?): NatsMessage {
        val message = mock<NatsMessage>()
        val metadata = metadata()
        val headers = Headers()
        traceparent?.let { headers.add(TraceParent.HEADER, it) }
        whenever(message.subject).thenReturn("bank-account.event.bank-accounts.sub")
        whenever(message.data).thenReturn(ByteArray(0))
        whenever(message.headers).thenReturn(headers)
        whenever(message.metaData()).thenReturn(metadata)
        return message
    }

    private fun metadata(): NatsJetStreamMetaData {
        val metadata = mock<NatsJetStreamMetaData>()
        whenever(metadata.streamSequence()).thenReturn(1L)
        whenever(metadata.deliveredCount()).thenReturn(1L)
        whenever(metadata.timestamp()).thenReturn(ZonedDateTime.now())
        return metadata
    }

    private fun converterReturningProtoEvent(): JetStreamMessageConverter<BankAccountEvent> {
        val converter = mock<JetStreamMessageConverter<BankAccountEvent>>()
        whenever(converter.deserialize(any(), any(), any())).thenReturn(protoEvent())
        return converter
    }

    private fun jetStreamMessage(
        traceparent: String?,
        extraHeaders: Map<String, List<String>> = emptyMap(),
    ): JetStreamMessage<BankAccountEvent> {
        val headers = extraHeaders + (traceparent?.let { mapOf(TraceParent.HEADER to listOf(it)) } ?: emptyMap())
        return JetStreamMessage(
            payload = protoEvent(),
            subject = "bank-account.event.bank-accounts.sub",
            messageId = "bank-account.event.bank-accounts-1",
            sequenceNumber = 1L,
            timestamp = Instant.now(),
            headers = headers,
        )
    }

    private fun protoEvent(): BankAccountEvent =
        BankAccountEvent
            .newBuilder()
            .setTransferWithdrawalStarted(
                TransferWithdrawalStarted
                    .newBuilder()
                    .setAggregateId("acc-123")
                    .setUpdatedAtEpochMillis(1000L)
                    .setTransactionId("tx-456")
                    .setAmount("100.50")
                    .build(),
            ).build()

    private fun channelObservingTraceIds(
        observedTraceIds: MutableList<String?>,
        channelSent: CountDownLatch,
    ): MessageChannel {
        val channel = mock<MessageChannel>()
        whenever(channel.send(any<Message<*>>())).thenAnswer {
            observedTraceIds.add(tracer.currentSpan()?.context()?.traceId())
            channelSent.countDown()
            true
        }
        return channel
    }

    private fun channelCapturingSentMessages(sent: MutableList<Message<AccountEvent>>): MessageChannel {
        val channel = mock<MessageChannel>()
        whenever(channel.send(any<Message<*>>())).thenAnswer {
            sent.add(it.getArgument(0))
            true
        }
        return channel
    }
}
