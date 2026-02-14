package mz.shared.connector.postgresql

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuration properties for PostgreSQL-backed message channel store.
 */
@ConfigurationProperties(prefix = "app.integration.postgresql")
data class PostgresqlIntegrationProperties(
    /**
     * Whether PostgreSQL-backed message store is enabled.
     */
    val enabled: Boolean = true,
    /**
     * List of trusted packages for JSON deserialization.
     * These packages are allowed to be deserialized from the message store.
     */
    val trustedPackages: List<String> =
        listOf(
            "org.springframework",
            "org.springframework.integration.gateway",
        ),
)
