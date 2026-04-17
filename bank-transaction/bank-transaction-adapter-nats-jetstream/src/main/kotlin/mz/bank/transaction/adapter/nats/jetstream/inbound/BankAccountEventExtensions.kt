package mz.bank.transaction.adapter.nats.jetstream.inbound

import mz.bank.account.contract.proto.BankAccountEvent
import mz.bank.transaction.application.account.AccountEvent
import mz.bank.transaction.adapter.redis.stream.inbound.toAccountEvent as redisToAccountEvent

/**
 * Reuses the existing BankAccountEvent → AccountEvent conversion
 * from the Redis Stream adapter module.
 */
fun BankAccountEvent.toInboundEvent(): AccountEvent = redisToAccountEvent()
