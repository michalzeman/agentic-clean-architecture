package mz.shared.connector.redis.json

import com.fasterxml.jackson.databind.Module
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.springframework.integration.support.json.JacksonJsonUtils

/**
 * Builder for creating messaging-aware ObjectMapper instances with customizable
 * trusted packages and modules.
 *
 * This builder follows the Open/Closed Principle - it provides sensible defaults
 * while allowing extension through custom packages and modules.
 *
 * Example usage:
 * ```kotlin
 * val objectMapper = MessagingObjectMapperBuilder()
 *     .withTrustedPackage("com.example.custom")
 *     .withModule(CustomModule())
 *     .build()
 *
 * // Or with multiple customizations
 * val objectMapper = MessagingObjectMapperBuilder()
 *     .withTrustedPackages(listOf("pkg1", "pkg2"))
 *     .withModules(listOf(Module1(), Module2()))
 *     .build()
 * ```
 */
class MessagingObjectMapperBuilder {
    private val trustedPackages =
        mutableSetOf(
            "mz", // project-specific package
            "java.math",
            "org.springframework.data.redis.connection.stream",
            "kotlin.collections",
        )

    private val modules =
        mutableListOf<Module>(
            KotlinModule.Builder().build(),
            JavaTimeModule(),
        )

    /**
     * Adds a single trusted package for deserialization.
     *
     * @param packageName The package name to trust
     * @return This builder for method chaining
     */
    fun withTrustedPackage(packageName: String): MessagingObjectMapperBuilder {
        trustedPackages.add(packageName)
        return this
    }

    /**
     * Adds multiple trusted packages for deserialization.
     *
     * @param packages The package names to trust
     * @return This builder for method chaining
     */
    fun withTrustedPackages(packages: Collection<String>): MessagingObjectMapperBuilder {
        trustedPackages.addAll(packages)
        return this
    }

    /**
     * Adds a single Jackson module to the ObjectMapper.
     *
     * @param module The module to register
     * @return This builder for method chaining
     */
    fun withModule(module: Module): MessagingObjectMapperBuilder {
        modules.add(module)
        return this
    }

    /**
     * Adds multiple Jackson modules to the ObjectMapper.
     *
     * @param modulesToAdd The modules to register
     * @return This builder for method chaining
     */
    fun withModules(modulesToAdd: Collection<Module>): MessagingObjectMapperBuilder {
        modules.addAll(modulesToAdd)
        return this
    }

    /**
     * Builds the configured ObjectMapper instance.
     *
     * @return A messaging-aware ObjectMapper with all configured packages and modules
     */
    fun build(): ObjectMapper {
        val objectMapper = JacksonJsonUtils.messagingAwareMapper(*trustedPackages.toTypedArray())
        modules.forEach { objectMapper.registerModule(it) }
        return objectMapper
    }
}
