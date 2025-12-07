package mz.shared.connector.redis.protobuf

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.protobuf.Message
import org.springframework.data.redis.connection.stream.MapRecord
import org.springframework.data.redis.connection.stream.StreamRecords
import org.springframework.data.redis.connection.stream.StringRecord
import org.springframework.messaging.support.MessageBuilder
import java.util.Base64
import org.springframework.messaging.Message as SpringMessage

private const val PAYLOAD = "payload"
private const val HEADERS = "headers"

/**
 * Serializer for Spring Messages containing Protocol Buffer messages.
 * Uses protobuf binary serialization for the payload and Jackson for headers.
 */
class RedisMapRecordProtobufSerializer(
    private val objectMapper: ObjectMapper,
) {
    fun <V : Message> serialize(message: SpringMessage<V>): StringRecord {
        val headers = objectMapper.writeValueAsString(message.headers)
        // Serialize protobuf message to binary and encode as Base64
        val payloadBytes = message.payload.toByteArray()
        val payloadBase64 = Base64.getEncoder().encodeToString(payloadBytes)
        return StreamRecords.string(mutableMapOf(PAYLOAD to payloadBase64, HEADERS to headers))
    }

    fun <V : Message> deserialize(
        record: MapRecord<String, String, String>,
        parser: (ByteArray) -> V,
    ): SpringMessage<V> {
        val headers = deserializeHeaders(record)
        val payload = deserializePayload(record, parser)

        return MessageBuilder
            .withPayload(payload)
            .copyHeaders(headers)
            .build()
    }

    private fun <V : Message> deserializePayload(
        record: MapRecord<String, String, String>,
        parser: (ByteArray) -> V,
    ): V {
        val payloadBase64 = record.value[PAYLOAD]!!
        val payloadBytes = Base64.getDecoder().decode(payloadBase64)
        return parser(payloadBytes)
    }

    private fun deserializeHeaders(record: MapRecord<String, String, String>): Map<String, String> {
        val headersValue = record.value[HEADERS]
        return headersValue?.let { objectMapper.readValue(it, object : TypeReference<Map<String, String>>() {}) }
            ?: emptyMap()
    }
}
