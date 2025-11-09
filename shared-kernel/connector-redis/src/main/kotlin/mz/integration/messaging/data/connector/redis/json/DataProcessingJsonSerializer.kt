package mz.integration.messaging.mz.integration.messaging.data.connector.redis.json

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.redis.serializer.RedisSerializer
import org.springframework.data.redis.serializer.SerializationException
import org.springframework.integration.mapping.InboundMessageMapper
import org.springframework.integration.support.json.Jackson2JsonMessageParser
import org.springframework.integration.support.json.JsonInboundMessageMapper
import org.springframework.messaging.Message

class DataProcessingJsonSerializer(
    private val objectMapper: ObjectMapper,
) : RedisSerializer<Message<*>> {
    private val mapper: InboundMessageMapper<String?> =
        JsonInboundMessageMapper(String::class.java, Jackson2JsonMessageParser())

    override fun serialize(value: Message<*>?): ByteArray {
        try {
            return this.objectMapper.writeValueAsBytes(value)
        } catch (e: JsonProcessingException) {
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
