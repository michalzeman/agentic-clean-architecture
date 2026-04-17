package mz.bank.transaction.adapter.nats.jetstream

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuration properties for Bank Transaction NATS JetStream adapter.
 */
@ConfigurationProperties(prefix = "adapters.nats-jetstream")
data class BankTransactionJetStreamProperties(
    val bankTransactionEventsStream: String = "bank-transaction.event.bank-transactions",
    val bankTransactionEventsSubject: String = "bank-transaction.events",
    val bankAccountEventsStream: String = "bank-account.event.bank-accounts",
    val bankAccountEventsSubject: String = "bank-account.events",
    val consumerName: String = "bank-transaction-consumer",
    val consumerGroup: String = "bank-transaction-service",
)
