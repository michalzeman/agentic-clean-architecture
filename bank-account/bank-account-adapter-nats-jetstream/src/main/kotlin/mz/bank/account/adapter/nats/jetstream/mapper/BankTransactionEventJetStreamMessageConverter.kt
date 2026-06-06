package mz.bank.account.adapter.nats.jetstream.mapper

import mz.bank.transaction.contract.proto.BankTransactionEvent
import mz.shared.connector.nats.jetstream.message.JetStreamMessageConverter
import tools.jackson.databind.ObjectMapper

class BankTransactionEventJetStreamMessageConverter(
    private val objectMapper: ObjectMapper,
) : JetStreamMessageConverter<BankTransactionEvent> {
    override val contentType: String = "application/x-protobuf"

    override fun serialize(payload: BankTransactionEvent): ByteArray = objectMapper.writeValueAsBytes(payload)

    override fun deserialize(
        data: ByteArray,
        subject: String,
        headers: Map<String, List<String>>,
    ): BankTransactionEvent = objectMapper.readValue(data, BankTransactionEvent::class.java)
}
