package mz.shared.connector.redis.json

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.redis.connection.stream.MapRecord
import org.springframework.data.redis.connection.stream.StreamRecords
import org.springframework.data.redis.connection.stream.StringRecord
import org.springframework.messaging.Message
import org.springframework.messaging.support.MessageBuilder

private const val PAYLOAD = "payload"

private const val HEADERS = "headers"

class RedisMapRecordJsonSerializer(
    val objectMapper: ObjectMapper,
) {
    fun <V> serialize(message: Message<V>): StringRecord {
        val headers = objectMapper.writeValueAsString(message.headers)
        val payload = objectMapper.writeValueAsString(message.payload)
        return StreamRecords.string(mutableMapOf(PAYLOAD to payload, HEADERS to headers))
    }

    fun <V> deserialize(
        record: MapRecord<String, String, String>,
        type: Class<V>,
    ): Message<V> {
        val headers = deserializeHeaders(record)

        val payload = deserializePayload(record, type)!!

        return MessageBuilder
            .withPayload<V>(payload)
            .copyHeaders(headers)
            .build()
    }

    private fun <V> deserializePayload(
        record: MapRecord<String, String, String>,
        type: Class<V>,
    ): V {
        val payloadValue = record.value[PAYLOAD]!!
        val payload: V = objectMapper.readValue(payloadValue, type)
        return payload
    }

    private fun deserializeHeaders(record: MapRecord<String, String, String>): Map<String, String> {
        val headersValue = record.value[HEADERS]
        return headersValue?.let { objectMapper.readValue(it, object : TypeReference<Map<String, String>>() {}) }
            ?: emptyMap()
    }
}
