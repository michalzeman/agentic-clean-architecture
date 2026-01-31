package mz.shared.connector.redis.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.hubspot.jackson.datatype.protobuf.ProtobufModule
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.integration.support.json.JacksonJsonUtils

/**
 * Auto-configuration for Protobuf ObjectMapper used by Redis Stream serialization.
 * Provides a pre-configured ObjectMapper for protobuf message serialization/deserialization.
 *
 * This ObjectMapper is used by:
 * - RedisMapRecordJsonSerializer for stream record serialization
 * - RedisStreamConsumer for deserializing incoming messages
 * - RedisStreamPublisher for serializing outgoing messages
 *
 * The ObjectMapper is configured with:
 * - ProtobufModule for protobuf message support
 * - KotlinModule for Kotlin data class support
 * - Messaging-aware mapper configuration for Spring Integration compatibility
 *
 * To override with a custom ObjectMapper:
 * ```kotlin
 * @Bean("protobufObjectMapper")
 * @Primary
 * fun customProtobufObjectMapper(): ObjectMapper {
 *     // Custom implementation
 * }
 * ```
 */
@Configuration
class ProtobufObjectMapperConfig {
    /**
     * Creates a messaging-aware ObjectMapper for protobuf serialization.
     * Only created if no bean named "protobufObjectMapper" exists.
     */
    @Bean("protobufObjectMapper")
    @ConditionalOnMissingBean(name = ["protobufObjectMapper"])
    fun protobufObjectMapper(): ObjectMapper {
        val objectMapper =
            JacksonJsonUtils.messagingAwareMapper(
                "mz",
                "java.math",
                "org.springframework.data.redis.connection.stream",
                "kotlin.collections",
            )
        objectMapper
            .registerModule(KotlinModule.Builder().build())
            .registerModule(ProtobufModule())
        return objectMapper
    }
}
