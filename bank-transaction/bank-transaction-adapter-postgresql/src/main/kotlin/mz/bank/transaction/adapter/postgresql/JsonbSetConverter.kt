package mz.bank.transaction.adapter.postgresql

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.postgresql.util.PGobject
import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.ReadingConverter
import org.springframework.data.convert.WritingConverter

private val objectMapper = jacksonObjectMapper()

/**
 * Converts Set<String> to a PostgreSQL JSONB PGobject for writing.
 */
@WritingConverter
internal class SetToJsonbConverter : Converter<Set<String>, PGobject> {
    override fun convert(source: Set<String>): PGobject =
        PGobject().apply {
            type = "jsonb"
            value = objectMapper.writeValueAsString(source)
        }
}

/**
 * Converts a PostgreSQL JSONB PGobject back to Set<String> for reading.
 */
@ReadingConverter
internal class JsonbToSetConverter : Converter<PGobject, Set<String>> {
    override fun convert(source: PGobject): Set<String> = objectMapper.readValue(source.value ?: "[]")
}
