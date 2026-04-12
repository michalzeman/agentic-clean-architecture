package mz.bank.transaction.adapter.nats.jetstream.mapper

import mz.bank.transaction.adapter.redis.stream.BankTransactionEventMapper
import org.springframework.stereotype.Component
import mz.bank.transaction.domain.BankTransactionEvent as DomainEvent

/**
 * Mapper for converting domain events to protobuf for JetStream publishing.
 * Reuses the existing BankTransactionEventMapper logic from Redis Stream adapter.
 */
@Component
class BankTransactionEventToJetStreamMapper {
    /**
     * Converts a domain BankTransactionEvent to protobuf bytes for JetStream.
     */
    fun toProto(domainEvent: DomainEvent): ByteArray {
        val protoEvent = BankTransactionEventMapper.toProto(domainEvent)
        return protoEvent.toByteArray()
    }
}
