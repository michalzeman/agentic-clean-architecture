package mz.bank.account.adapter.nats.jetstream.outbound

import mz.bank.account.adapter.nats.jetstream.BankAccountJetStreamProperties
import mz.bank.account.adapter.nats.jetstream.mapper.BankAccountEventToJetStreamMapper
import mz.bank.account.contract.proto.BankAccountEvent
import mz.shared.connector.nats.jetstream.producer.JetStreamProducer
import mz.shared.connector.nats.jetstream.producer.JetStreamPublishRequest
import org.slf4j.LoggerFactory
import mz.bank.account.domain.BankAccountEvent as DomainEvent

private val logger = LoggerFactory.getLogger(JetStreamBankAccountEventsPublisher::class.java)

/**
 * Service activator that publishes bank account domain events to NATS JetStream.
 * Converts domain events to protobuf and publishes to the configured stream.
 */
class JetStreamBankAccountEventsPublisher(
    private val jetStreamProducer: JetStreamProducer<BankAccountEvent>,
    private val mapper: BankAccountEventToJetStreamMapper,
    private val properties: BankAccountJetStreamProperties,
) {
    /**
     * Publishes a bank account event to JetStream.
     */
    suspend fun publish(event: DomainEvent) {
        val protoEvent = BankAccountEvent.parseFrom(mapper.toProto(event))

        val request =
            JetStreamPublishRequest(
                subject = properties.bankAccountEventsSubject,
                payload = protoEvent,
                messageId = generateMessageId(event),
            )

        logger.info(
            "[Publisher:Boundary] - eventType={}, aggregateId={}, stream={}, subject={}",
            event::class.simpleName,
            event.aggregateId.value,
            properties.bankAccountEventsStream,
            properties.bankAccountEventsSubject,
        )

        jetStreamProducer.publish(request)
    }

    private fun generateMessageId(event: DomainEvent): String {
        val timestamp = event.updatedAt.toEpochMilli()
        val id = event.aggregateId.value
        val nano = System.nanoTime()
        return "$timestamp-$id-$nano"
    }
}
