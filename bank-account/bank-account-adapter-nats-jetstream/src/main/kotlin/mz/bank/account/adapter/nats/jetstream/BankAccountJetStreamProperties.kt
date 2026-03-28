package mz.bank.account.adapter.nats.jetstream

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuration properties for Bank Account NATS JetStream adapter.
 */
@ConfigurationProperties(prefix = "adapters.nats-jetstream")
data class BankAccountJetStreamProperties(
    val bankAccountEventsStream: String = "bank-account.event.bank-accounts",
    val bankAccountEventsSubject: String = "bank-account.events",
    val bankTransactionEventsStream: String = "bank-transaction.event.bank-transactions",
    val bankTransactionEventsSubject: String = "bank-transaction.events",
    val consumerName: String = "bank-account-consumer",
    val consumerGroup: String = "bank-account-service",
)
