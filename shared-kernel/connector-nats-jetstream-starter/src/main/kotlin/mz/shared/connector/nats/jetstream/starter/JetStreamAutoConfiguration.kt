package mz.shared.connector.nats.jetstream.starter

import mz.shared.connector.nats.DefaultNatsConnectionFactory
import mz.shared.connector.nats.DefaultNatsConnectionHealthIndicator
import mz.shared.connector.nats.NatsConnectionFactory
import mz.shared.connector.nats.NatsConnectionHealthIndicator
import mz.shared.connector.nats.NatsConnectionProperties
import mz.shared.connector.nats.jetstream.DefaultJetStreamHealthIndicator
import mz.shared.connector.nats.jetstream.JetStreamHealthIndicator
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration

/**
 * Spring Boot auto-configuration for NATS JetStream.
 *
 * Auto-configures NATS connection, JetStream context, and provides
 * beans for producers, consumers, and management operations.
 *
 * Enable with: nats.jetstream.enabled=true (default)
 */
@Configuration
@AutoConfiguration
@EnableConfigurationProperties(JetStreamProperties::class, NatsConnectionProperties::class)
@ComponentScan(basePackages = ["mz.shared.connector.nats.jetstream.starter"])
@ConditionalOnProperty(
    prefix = "nats.jetstream",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
class JetStreamAutoConfiguration {
    /**
     * Provides NATS connection factory for establishing connections
     * to NATS server.
     */
    @Bean
    @ConditionalOnMissingBean
    fun natsConnectionFactory(properties: NatsConnectionProperties): NatsConnectionFactory = DefaultNatsConnectionFactory(properties)

    /**
     * Provides health indicator for NATS connection monitoring.
     */
    @Bean
    @ConditionalOnMissingBean
    fun natsConnectionHealthIndicator(connectionFactory: NatsConnectionFactory): NatsConnectionHealthIndicator =
        DefaultNatsConnectionHealthIndicator(connectionFactory)

    /**
     * Provides JetStream health indicator for monitoring connection
     * and stream/consumer health.
     */
    @Bean
    @ConditionalOnMissingBean
    fun jetStreamHealthIndicator(connectionFactory: NatsConnectionFactory): JetStreamHealthIndicator =
        DefaultJetStreamHealthIndicator(connectionFactory)

    /**
     * Initializes JetStream streams at application startup.
     *
     * This bean runs at phase 0 (earliest), ensuring streams exist before
     * consumers attempt to subscribe (which run at default phase).
     */
    @Bean
    fun jetStreamStreamInitializer(
        connectionFactory: NatsConnectionFactory,
        properties: JetStreamProperties,
    ): JetStreamStreamInitializer = JetStreamStreamInitializer(connectionFactory.getConnection(), properties)
}
