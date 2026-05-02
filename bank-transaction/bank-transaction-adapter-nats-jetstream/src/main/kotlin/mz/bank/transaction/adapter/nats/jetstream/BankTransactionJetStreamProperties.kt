package mz.bank.transaction.adapter.nats.jetstream

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "adapters.nats-jetstream")
data class BankTransactionJetStreamProperties(
    val bankTransactionEventsStream: String,
    val bankTransactionEventsSubject: String,
    val bankAccountEventsStream: String,
    val bankAccountEventsSubject: String,
    val consumerName: String,
    val consumerGroup: String,
)
