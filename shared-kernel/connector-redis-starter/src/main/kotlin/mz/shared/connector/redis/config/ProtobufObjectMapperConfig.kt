package mz.shared.connector.redis.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.hubspot.jackson.datatype.protobuf.ProtobufModule
import mz.shared.connector.redis.json.MessagingObjectMapperBuilder
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

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
    fun protobufObjectMapper(): ObjectMapper =
        MessagingObjectMapperBuilder()
            .withModule(ProtobufModule())
            .build()
}
