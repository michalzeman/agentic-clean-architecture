package mz.shared.connector.postgresql

import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.integration.jdbc.store.JdbcChannelMessageStore
import org.springframework.integration.jdbc.store.channel.PostgresChannelMessageStoreQueryProvider
import javax.sql.DataSource

/**
 * Auto-configuration for PostgreSQL-backed channel message store.
 * Replaces RedisChannelMessageStore with JdbcChannelMessageStore using PostgreSQL.
 *
 * This configuration creates JdbcChannelMessageStore beans for both JSON and binary serialization.
 */
@AutoConfiguration
@ConditionalOnClass(JdbcChannelMessageStore::class)
@ConditionalOnProperty(
    prefix = "app.integration.postgresql",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
class DataPlatformPostgresqlConfiguration {
    /**
     * JSON-based channel message store for domain events and commands.
     * Uses PostgreSQL for human-readable message storage.
     */
    @Bean
    @ConditionalOnMissingBean(name = ["jsonJdbcChannelMessageStore"])
    fun jsonJdbcChannelMessageStore(dataSource: DataSource): JdbcChannelMessageStore {
        val store = JdbcChannelMessageStore(dataSource)
        store.setChannelMessageStoreQueryProvider(PostgresChannelMessageStoreQueryProvider())
        store.setRegion("json")
        return store
    }

    /**
     * Binary channel message store for protobuf events.
     * Uses BYTEA for efficient binary storage.
     */
    @Bean
    @ConditionalOnMissingBean(name = ["protoJdbcChannelMessageStore"])
    fun protoJdbcChannelMessageStore(dataSource: DataSource): JdbcChannelMessageStore {
        val store = JdbcChannelMessageStore(dataSource)
        store.setChannelMessageStoreQueryProvider(PostgresChannelMessageStoreQueryProvider())
        store.setRegion("proto")
        return store
    }
}
