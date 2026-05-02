package mz.bank.transaction.adapter.nats.jetstream.mapper

import com.fasterxml.jackson.databind.ObjectMapper
import mz.bank.account.contract.proto.BankAccountEvent
import mz.shared.connector.nats.jetstream.message.JetStreamMessageConverter

/**
 * JetStream message converter for inbound BankAccountEvent protobuf messages.
 */
class BankAccountEventJetStreamMessageConverter(
    private val objectMapper: ObjectMapper,
) : JetStreamMessageConverter<BankAccountEvent> {
    override val contentType: String = "application/x-protobuf"

    override fun serialize(payload: BankAccountEvent): ByteArray = objectMapper.writeValueAsBytes(payload)

    override fun deserialize(
        data: ByteArray,
        subject: String,
        headers: Map<String, List<String>>,
    ): BankAccountEvent = objectMapper.readValue(data, BankAccountEvent::class.java)
}
