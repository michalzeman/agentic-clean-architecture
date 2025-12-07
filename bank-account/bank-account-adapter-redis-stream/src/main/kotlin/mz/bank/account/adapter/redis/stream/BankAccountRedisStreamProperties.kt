package mz.bank.account.adapter.redis.stream

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "adapters.redis.stream")
data class BankAccountRedisStreamProperties(
    val bankAccountEventsStream: String,
)
