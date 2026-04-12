package mz.bank.transaction.adapter.nats.jetstream

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "adapters.nats-jetstream")
data class BankTransactionJetStreamProperties(
    val bankTransactionEventsStream: String = "bank-transaction.event.bank-transactions",
    val bankTransactionEventsSubject: String = "bank-transaction.events",
)
