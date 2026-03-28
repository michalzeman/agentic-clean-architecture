package mz.bank.account.adapter.nats.jetstream.inbound

import mz.bank.account.adapter.redis.stream.inbound.toInboundEvent
import mz.bank.transaction.contract.proto.BankTransactionEvent

/**
 * Reuses the existing BankTransactionEvent to InboundBankTransactionEvent conversion
 * from the Redis Stream adapter module.
 */
fun BankTransactionEvent.toInboundEvent(): mz.bank.account.application.transaction.InboundBankTransactionEvent = this.toInboundEvent()
