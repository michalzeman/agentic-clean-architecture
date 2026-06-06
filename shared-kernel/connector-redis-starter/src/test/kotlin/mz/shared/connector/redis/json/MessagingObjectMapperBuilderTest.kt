package mz.shared.connector.redis.json

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.messaging.support.GenericMessage
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.JacksonModule
import tools.jackson.module.kotlin.KotlinModule
import java.math.BigDecimal
import java.time.Instant

class MessagingObjectMapperBuilderTest {
    @Test
    fun `should create ObjectMapper with default configuration`() {
        val objectMapper = MessagingObjectMapperBuilder().build()

        assertThat(objectMapper).isNotNull
    }

    @Test
    fun `should serialize and deserialize GenericMessage with default trusted packages`() {
        val objectMapper = MessagingObjectMapperBuilder().build()
        val message = GenericMessage("test payload")

        val json = objectMapper.writeValueAsString(message)
        val deserialized = objectMapper.readValue(json, GenericMessage::class.java)

        assertThat(deserialized.payload).isEqualTo("test payload")
    }

    @Test
    fun `should support java math package by default`() {
        val objectMapper = MessagingObjectMapperBuilder().build()
        val value = BigDecimal("123.45")

        val json = objectMapper.writeValueAsString(value)
        val deserialized = objectMapper.readValue(json, BigDecimal::class.java)

        assertThat(deserialized).isEqualByComparingTo(value)
    }

    @Test
    fun `should support Java time by default in Jackson 3`() {
        val objectMapper = MessagingObjectMapperBuilder().build()
        val instant = Instant.parse("2024-01-15T10:30:00Z")

        val json = objectMapper.writeValueAsString(instant)
        val deserialized = objectMapper.readValue(json, Instant::class.java)

        assertThat(deserialized).isEqualTo(instant)
    }

    @Test
    fun `should add custom trusted package`() {
        val objectMapper =
            MessagingObjectMapperBuilder()
                .withTrustedPackage("org.springframework.integration.gateway")
                .build()

        assertThat(objectMapper).isNotNull
    }

    @Test
    fun `should add multiple custom trusted packages`() {
        val objectMapper =
            MessagingObjectMapperBuilder()
                .withTrustedPackages(
                    listOf(
                        "org.springframework.integration.gateway",
                        "com.example.custom",
                    ),
                ).build()

        assertThat(objectMapper).isNotNull
    }

    @Test
    fun `should add custom module`() {
        val customModule = KotlinModule.Builder().build()

        val objectMapper =
            MessagingObjectMapperBuilder()
                .withModule(customModule)
                .build()

        assertThat(objectMapper).isNotNull
    }

    @Test
    fun `should add multiple custom modules`() {
        val modules: List<JacksonModule> =
            listOf(
                KotlinModule.Builder().build(),
                // JavaTimeModule is built into Jackson 3
            )

        val objectMapper =
            MessagingObjectMapperBuilder()
                .withModules(modules)
                .build()

        assertThat(objectMapper).isNotNull
    }

    @Test
    fun `should support chaining methods`() {
        val objectMapper =
            MessagingObjectMapperBuilder()
                .withTrustedPackage("org.springframework.integration.gateway")
                .withTrustedPackage("com.example.another")
                .withTrustedPackages(listOf("pkg1", "pkg2"))
                .withModules(listOf(KotlinModule.Builder().build()))
                .build()

        assertThat(objectMapper).isNotNull
    }

    @Test
    fun `should serialize and deserialize Kotlin data class`() {
        val objectMapper = MessagingObjectMapperBuilder().build()
        val data = TestDataClass(name = "test", value = 42)

        val json = objectMapper.writeValueAsString(data)
        val deserialized = objectMapper.readValue(json, TestDataClass::class.java)

        assertThat(deserialized).isEqualTo(data)
    }

    @Test
    fun `should serialize and deserialize list of Kotlin data classes`() {
        val objectMapper = MessagingObjectMapperBuilder().build()
        val data = listOf(TestDataClass(name = "a", value = 1), TestDataClass(name = "b", value = 2))

        val json = objectMapper.writeValueAsString(data)
        val typeRef = object : TypeReference<List<TestDataClass>>() {}
        val deserialized = objectMapper.readValue(json, typeRef)

        assertThat(deserialized).hasSize(2)
        assertThat(deserialized[0].name).isEqualTo("a")
        assertThat(deserialized[1].value).isEqualTo(2)
    }

    data class TestDataClass(
        val name: String,
        val value: Int,
    )
}
