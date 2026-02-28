package mz.bank.transaction.adapter.postgresql

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.postgresql.util.PGobject

class JsonbSetConverterTest {
    private val setToJsonb = SetToJsonbConverter()
    private val jsonbToSet = JsonbToSetConverter()

    // ==================== SetToJsonbConverter Tests ====================

    @Test
    fun `should convert Set to PGobject with jsonb type`() {
        // Given
        val set = setOf("tag-1", "tag-2", "tag-3")

        // When
        val result = setToJsonb.convert(set)

        // Then
        assertThat(result.type).isEqualTo("jsonb")
        assertThat(result.value).contains("tag-1", "tag-2", "tag-3")
    }

    @Test
    fun `should convert empty Set to PGobject with empty JSON array`() {
        // Given
        val set = emptySet<String>()

        // When
        val result = setToJsonb.convert(set)

        // Then
        assertThat(result.type).isEqualTo("jsonb")
        assertThat(result.value).isEqualTo("[]")
    }

    // ==================== JsonbToSetConverter Tests ====================

    @Test
    fun `should convert PGobject JSON array to Set`() {
        // Given
        val pgObject =
            PGobject().apply {
                type = "jsonb"
                value = """["tag-1","tag-2"]"""
            }

        // When
        val result = jsonbToSet.convert(pgObject)

        // Then
        assertThat(result).containsExactlyInAnyOrder("tag-1", "tag-2")
    }

    @Test
    fun `should convert PGobject with null value to empty Set`() {
        // Given
        val pgObject =
            PGobject().apply {
                type = "jsonb"
                value = null
            }

        // When
        val result = jsonbToSet.convert(pgObject)

        // Then
        assertThat(result).isEmpty()
    }

    // ==================== Round-trip Tests ====================

    @Test
    fun `should round-trip convert Set through PGobject`() {
        // Given
        val original = setOf("tag-a", "tag-b", "tag-c")

        // When
        val pgObject = setToJsonb.convert(original)
        val result = jsonbToSet.convert(pgObject)

        // Then
        assertThat(result).isEqualTo(original)
    }
}
