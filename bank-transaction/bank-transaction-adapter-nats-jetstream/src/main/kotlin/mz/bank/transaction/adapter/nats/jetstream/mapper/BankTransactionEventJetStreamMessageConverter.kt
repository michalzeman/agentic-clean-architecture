package mz.bank.transaction.adapter.nats.jetstream.mapper

import com.fasterxml.jackson.databind.ObjectMapper
import mz.bank.transaction.contract.proto.BankTransactionEvent
import mz.shared.connector.nats.jetstream.message.JetStreamMessageConverter
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

/**
 * JetStream message converter for BankTransactionEvent using Jackson protobuf ObjectMapper.
 */
@Component
class BankTransactionEventJetStreamMessageConverter(
    @Qualifier("protobufObjectMapper") private val objectMapper: ObjectMapper,
) : JetStreamMessageConverter<BankTransactionEvent> {
    override val contentType: String = "application/x-protobuf"

    override fun serialize(payload: BankTransactionEvent): ByteArray = objectMapper.writeValueAsBytes(payload)

    override fun deserialize(
        data: ByteArray,
        subject: String,
        headers: Map<String, List<String>>,
    ): BankTransactionEvent = objectMapper.readValue(data, BankTransactionEvent::class.java)
}
