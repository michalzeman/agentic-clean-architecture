package mz.bank.transaction.adapter.redis.stream

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "adapters.redis.stream")
data class BankTransactionRedisStreamProperties(
    val bankTransactionEventsStream: String,
)
