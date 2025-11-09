package mz.integration.messaging.data.domain

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.ReadingConverter
import org.springframework.data.convert.WritingConverter
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer
import org.springframework.stereotype.Component

@Component
@WritingConverter
class JobDocumentConverter(
    objectMapper: ObjectMapper,
) : Converter<JobDocument, ByteArray> {
    private var serializer: Jackson2JsonRedisSerializer<JobDocument> =
        Jackson2JsonRedisSerializer<JobDocument>(objectMapper, JobDocument::class.java)

    override fun convert(source: JobDocument): ByteArray? = serializer.serialize(source)
}

@Component
@ReadingConverter
class BytesToDocumentConverter(
    objectMapper: ObjectMapper,
) : Converter<ByteArray, JobDocument> {
    private var serializer: Jackson2JsonRedisSerializer<JobDocument> =
        Jackson2JsonRedisSerializer<JobDocument>(objectMapper, JobDocument::class.java)

    override fun convert(source: ByteArray): JobDocument = serializer.deserialize(source)!!
}
