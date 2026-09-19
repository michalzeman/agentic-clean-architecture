package mz.shared.connector.postgresql

import org.springframework.core.serializer.Deserializer
import org.springframework.core.serializer.Serializer
import org.springframework.integration.jdbc.store.JdbcChannelMessageStore
import org.springframework.integration.support.json.JacksonMessagingUtils
import org.springframework.messaging.Message
import org.springframework.messaging.support.GenericMessage
import tools.jackson.module.kotlin.KotlinModule
import java.io.InputStream
import java.io.OutputStream

/**
 * Messaging-aware JSON serializer wiring for a [JdbcChannelMessageStore]. The whole
 * Message — headers included, so the W3C traceparent header rides every stored message —
 * goes through one Jackson mapper with trusted packages and Kotlin support. Shared by the
 * auto-configured Postgres store and the H2-based trace-context test.
 */
internal object JsonChannelMessageStoreSerializers {
    fun configure(
        store: JdbcChannelMessageStore,
        trustedPackages: List<String>,
    ) {
        val mapper =
            JacksonMessagingUtils
                .messagingAwareMapper(*trustedPackages.toTypedArray())
                .rebuild()
                .addModule(KotlinModule.Builder().build())
                .build()

        store.setSerializer(
            object : Serializer<Message<*>> {
                override fun serialize(
                    message: Message<*>,
                    outputStream: OutputStream,
                ) {
                    mapper.writeValue(outputStream, message)
                }
            },
        )

        store.setDeserializer(
            object : Deserializer<Message<*>> {
                @Suppress("UNCHECKED_CAST")
                override fun deserialize(inputStream: InputStream): Message<*> =
                    mapper.readValue(inputStream, GenericMessage::class.java) as Message<*>
            },
        )
    }
}
