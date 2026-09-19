package mz.shared.connector.postgresql

import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
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
@EnableConfigurationProperties(PostgresqlIntegrationProperties::class)
class DataPlatformPostgresqlConfiguration {
    /**
     * JSON-based channel message store for domain events and commands.
     * Uses PostgreSQL for human-readable message storage.
     */
    @Bean
    @ConditionalOnMissingBean(name = ["jsonJdbcChannelMessageStore"])
    fun jsonJdbcChannelMessageStore(
        dataSource: DataSource,
        properties: PostgresqlIntegrationProperties,
    ): JdbcChannelMessageStore {
        val store = JdbcChannelMessageStore(dataSource)
        store.setChannelMessageStoreQueryProvider(PostgresChannelMessageStoreQueryProvider())
        store.setRegion("json")
        JsonChannelMessageStoreSerializers.configure(store, properties.trustedPackages)
        return store
    }

    /**
     * Proto-region channel store on Spring Integration's default Java serialization: the
     * whole `GenericMessage`, headers included, round-trips through the store, so the
     * traceparent header survives, but without the explicit Jackson header path the JSON
     * region uses. Pre-existing behavior, kept as is; flagged as info finding F15 in the
     * phase-2 review.
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
