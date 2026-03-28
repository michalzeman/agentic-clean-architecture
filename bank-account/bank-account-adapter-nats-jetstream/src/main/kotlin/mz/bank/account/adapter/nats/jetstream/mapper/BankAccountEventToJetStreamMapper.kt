package mz.bank.account.adapter.nats.jetstream.mapper

import mz.bank.account.adapter.redis.stream.BankAccountEventMapper
import org.springframework.stereotype.Component
import mz.bank.account.domain.BankAccountEvent as DomainEvent

/**
 * Mapper for converting domain events to protobuf for JetStream publishing.
 * Reuses the existing BankAccountEventMapper logic from Redis Stream adapter.
 */
@Component
class BankAccountEventToJetStreamMapper {
    /**
     * Converts a domain BankAccountEvent to protobuf bytes for JetStream.
     */
    fun toProto(domainEvent: DomainEvent): ByteArray {
        val protoEvent = BankAccountEventMapper.toProto(domainEvent)
        return protoEvent.toByteArray()
    }
}
