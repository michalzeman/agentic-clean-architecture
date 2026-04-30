package mz.bank.transaction.adapter.nats.jetstream.outbound

import mz.bank.transaction.adapter.nats.jetstream.BankTransactionJetStreamProperties
import mz.bank.transaction.adapter.nats.jetstream.mapper.BankTransactionEventToJetStreamMapper
import mz.bank.transaction.contract.proto.BankTransactionEvent
import mz.shared.connector.nats.jetstream.producer.JetStreamProducer
import mz.shared.connector.nats.jetstream.producer.JetStreamPublishRequest
import org.slf4j.LoggerFactory
import mz.bank.transaction.domain.BankTransactionEvent as DomainEvent

private val logger = LoggerFactory.getLogger(JetStreamBankTransactionEventsPublisher::class.java)

/**
 * Service activator that publishes bank transaction domain events to NATS JetStream.
 * Converts domain events to protobuf and publishes to the configured stream.
 */
class JetStreamBankTransactionEventsPublisher(
    private val jetStreamProducer: JetStreamProducer<BankTransactionEvent>,
    private val mapper: BankTransactionEventToJetStreamMapper,
    private val properties: BankTransactionJetStreamProperties,
) {
    /**
     * Publishes a bank transaction event to JetStream.
     */
    suspend fun publish(event: DomainEvent) {
        val protoEvent = BankTransactionEvent.parseFrom(mapper.toProto(event))

        val request =
            JetStreamPublishRequest(
                subject = properties.bankTransactionEventsSubject,
                payload = protoEvent,
                messageId = generateMessageId(event),
            )

        logger.info(
            "Publishing bank transaction event to JetStream: stream={}, subject={}",
            properties.bankTransactionEventsStream,
            properties.bankTransactionEventsSubject,
        )

        jetStreamProducer.publish(request)
    }

    private fun generateMessageId(event: DomainEvent): String {
        val timestamp = event.updatedAt.toEpochMilli()
        val id = event.aggregateId.value
        return "$timestamp-$id"
    }
}
