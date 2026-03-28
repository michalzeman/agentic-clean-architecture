package mz.bank.account.adapter.nats.jetstream.inbound

import mz.bank.account.application.transaction.InboundBankTransactionEvent
import mz.bank.transaction.contract.proto.BankTransactionEvent
import mz.bank.account.adapter.redis.stream.inbound.toInboundEvent as redisToInboundEvent

/**
 * Reuses the existing BankTransactionEvent to InboundBankTransactionEvent conversion
 * from the Redis Stream adapter module.
 */
fun BankTransactionEvent.toInboundEvent(): InboundBankTransactionEvent = redisToInboundEvent()
