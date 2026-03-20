package mz.shared.connector.nats.jetstream.message

/**
 * Contract for serializing domain objects to JetStream message payloads.
 *
 * @param T The domain type to serialize
 */
interface JetStreamSerializer<T> {
    /**
     * Serializes a domain object to bytes.
     */
    fun serialize(payload: T): ByteArray

    /**
     * Content type of the serialized format (e.g., "application/json", "application/protobuf")
     */
    val contentType: String
}

/**
 * Contract for deserializing JetStream message payloads to domain objects.
 *
 * @param T The domain type to deserialize to
 */
interface JetStreamDeserializer<T> {
    /**
     * Deserializes bytes to a domain object.
     *
     * @param data The raw message bytes
     * @param subject The subject the message was received on (for type dispatch)
     * @param headers Message headers that may contain type info
     */
    fun deserialize(
        data: ByteArray,
        subject: String,
        headers: Map<String, List<String>> = emptyMap(),
    ): T

    /**
     * Content type this deserializer handles.
     */
    val contentType: String
}

/**
 * Combined serializer/deserializer for bidirectional conversion.
 */
interface JetStreamMessageConverter<T> :
    JetStreamSerializer<T>,
    JetStreamDeserializer<T>

/**
 * Factory for obtaining appropriate converters based on content type or subject.
 */
interface JetStreamMessageConverterFactory {
    /**
     * Gets a converter for the given content type.
     */
    fun <T> getConverter(contentType: String): JetStreamMessageConverter<T>

    /**
     * Gets a converter for the given subject pattern.
     */
    fun <T> getConverterForSubject(subject: String): JetStreamMessageConverter<T>
}

/**
 * Exception thrown during message serialization/deserialization.
 */
class JetStreamMessageConversionException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
