package mz.shared.connector.postgresql

import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.core.serializer.Deserializer
import org.springframework.core.serializer.Serializer
import org.springframework.integration.jdbc.store.JdbcChannelMessageStore
import org.springframework.integration.jdbc.store.channel.PostgresChannelMessageStoreQueryProvider
import org.springframework.integration.support.json.JacksonMessagingUtils
import org.springframework.messaging.Message
import org.springframework.messaging.support.GenericMessage
import tools.jackson.module.kotlin.KotlinModule
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

        // Build messaging-aware ObjectMapper with trusted packages and Kotlin support
        val objectMapper =
            JacksonMessagingUtils
                .messagingAwareMapper(*properties.trustedPackages.toTypedArray())
                .rebuild()
                .addModule(KotlinModule.Builder().build())
                .build()

        store.setSerializer(
            object : Serializer<Message<*>> {
                override fun serialize(
                    message: Message<*>,
                    outputStream: java.io.OutputStream,
                ) {
                    objectMapper.writeValue(outputStream, message)
                }
            },
        )

        store.setDeserializer(
            object : Deserializer<Message<*>> {
                @Suppress("UNCHECKED_CAST")
                override fun deserialize(inputStream: java.io.InputStream): Message<*> =
                    objectMapper.readValue(inputStream, GenericMessage::class.java) as Message<*>
            },
        )

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
