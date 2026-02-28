package mz.bank.account.adapter.postgresql

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.postgresql.util.PGobject
import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.ReadingConverter
import org.springframework.data.convert.WritingConverter

private val objectMapper = jacksonObjectMapper()

/**
 * Converts a Set<String> to a PostgreSQL JSONB PGobject for storage.
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
 * Converts a PostgreSQL JSONB PGobject back to a Set<String> on read.
 */
@ReadingConverter
internal class JsonbToSetConverter : Converter<PGobject, Set<String>> {
    override fun convert(source: PGobject): Set<String> = source.value?.let { objectMapper.readValue(it) } ?: emptySet()
}
