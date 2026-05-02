package mz.bank.account.adapter.nats.jetstream

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "adapters.nats-jetstream")
data class BankAccountJetStreamProperties(
    val bankAccountEventsStream: String,
    val bankAccountEventsSubject: String,
    val bankTransactionEventsStream: String,
    val bankTransactionEventsSubject: String,
    val consumerName: String,
    val consumerGroup: String,
)
