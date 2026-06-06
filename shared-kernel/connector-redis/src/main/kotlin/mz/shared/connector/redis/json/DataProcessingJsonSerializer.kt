package mz.shared.connector.redis.json

import org.springframework.data.redis.serializer.RedisSerializer
import org.springframework.data.redis.serializer.SerializationException
import org.springframework.integration.mapping.InboundMessageMapper
import org.springframework.integration.support.json.JacksonJsonMessageParser
import org.springframework.integration.support.json.JsonInboundMessageMapper
import org.springframework.messaging.Message
import tools.jackson.core.JacksonException
import tools.jackson.databind.ObjectMapper

class DataProcessingJsonSerializer(
    private val objectMapper: ObjectMapper,
) : RedisSerializer<Message<*>> {
    private val mapper: InboundMessageMapper<String> =
        JsonInboundMessageMapper(String::class.java, JacksonJsonMessageParser())

    override fun serialize(value: Message<*>?): ByteArray {
        try {
            return this.objectMapper.writeValueAsBytes(value)
        } catch (e: JacksonException) {
            throw SerializationException("Fail to serialize 'message' to json.", e)
        }
    }

    override fun deserialize(bytes: ByteArray?): Message<*> {
        try {
            return mapper.toMessage(String(bytes!!))!!
        } catch (e: Exception) {
            throw SerializationException("Fail to deserialize 'message' from json.", e)
        }
    }
}
