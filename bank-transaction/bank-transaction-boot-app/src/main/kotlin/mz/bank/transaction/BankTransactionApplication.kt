package mz.bank.transaction

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import mz.shared.connector.redis.json.MessagingObjectMapperBuilder
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.core.serializer.Deserializer
import org.springframework.core.serializer.Serializer
import org.springframework.integration.jdbc.store.JdbcChannelMessageStore
import org.springframework.integration.jdbc.store.channel.PostgresChannelMessageStoreQueryProvider
import org.springframework.messaging.Message
import org.springframework.messaging.support.GenericMessage
import javax.sql.DataSource

@SpringBootApplication
class BankTransactionApplication {
    /**
     * JSON-based JDBC channel message store for domain events and commands.
     * Replaces RedisChannelMessageStore with PostgreSQL-backed storage.
     */
    @Bean
    fun jsonJdbcChannelMessageStore(dataSource: DataSource): JdbcChannelMessageStore {
        val store = JdbcChannelMessageStore(dataSource)
        store.setChannelMessageStoreQueryProvider(PostgresChannelMessageStoreQueryProvider())
        store.setRegion("json")
        // Use the messaging-aware object mapper for JSON serialization with trusted packages
        // org.springframework.integration.gateway is required for MonoReplyChannel deserialization
        val objectMapper =
            MessagingObjectMapperBuilder()
                .withTrustedPackage("org.springframework.integration.gateway")
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
     * Binary JDBC channel message store for protobuf events.
     * Uses BYTEA for efficient binary storage.
     */
    @Bean
    fun protoJdbcChannelMessageStore(dataSource: DataSource): JdbcChannelMessageStore {
        val store = JdbcChannelMessageStore(dataSource)
        store.setChannelMessageStoreQueryProvider(PostgresChannelMessageStoreQueryProvider())
        store.setRegion("proto")
        return store
    }

    @Bean
    @Primary
    fun objectMapper(): ObjectMapper {
        val objectMapper = ObjectMapper()
        objectMapper.registerModule(KotlinModule.Builder().build())
        objectMapper.registerModule(JavaTimeModule())
        return objectMapper
    }
}

fun main(args: Array<String>) {
    runApplication<BankTransactionApplication>(*args)
}
