# Redis Message Store & Channel Configuration

## Overview
Configuration patterns for RedisChannelMessageStore and MessageChannel beans with JSON or Protobuf serialization.

## Configuration Templates

### 1. JSON Serialization (for Domain Events/Commands)

```kotlin
@Configuration
class MessageStoreConfiguration(
    @Value("\${application.identifier}") private val applicationIdentifier: String,
) {
    @Bean
    fun genericJackson2JsonRedisSerializer(): GenericJackson2JsonRedisSerializer {
        val mapper = JacksonJsonUtils.messagingAwareMapper(
            "mz.integration.messaging",
            "mz.data.platform", // project-specific package
            "org.springframework.data.redis.connection.stream",
            "kotlin.collections",
        )
        mapper
            .registerModule(KotlinModule.Builder().build())
            .registerModule(ProtobufModule())
        return GenericJackson2JsonRedisSerializer(mapper)
    }

    @Bean
    fun jsonRedisChannelMessageStore(
        connectionFactory: LettuceConnectionFactory,
        genericJackson2JsonRedisSerializer: GenericJackson2JsonRedisSerializer,
    ): RedisChannelMessageStore =
        RedisChannelMessageStore(connectionFactory).apply {
            setValueSerializer(genericJackson2JsonRedisSerializer)
        }

    @Bean
    fun domainEventsChannel(jsonRedisChannelMessageStore: RedisChannelMessageStore) =
        MessageChannels
            .queue(
                "$applicationIdentifier.persistence.domain-event.channel",
                jsonRedisChannelMessageStore,
                "$applicationIdentifier.persistence.domain-event.storage",
            )
            .apply { datatype(DomainEvent::class.java) }
            .get()
}
```

### 2. Protobuf Serialization (for Redis Streams)

```kotlin
@Bean
fun protoRedisChannelMessageStore(
    connectionFactory: LettuceConnectionFactory
): RedisChannelMessageStore = 
    RedisChannelMessageStore(connectionFactory) // uses default byte array serialization

@Bean
fun streamMessageChannel(protoRedisChannelMessageStore: RedisChannelMessageStore) =
    MessageChannels
        .queue(
            "$applicationIdentifier.persistence.stream-message.channel",
            protoRedisChannelMessageStore,
            "$applicationIdentifier.persistence.stream-message.storage",
        )
        .apply { datatype(StreamMessageProto::class.java) }
        .get()
```

## Usage Guidelines

**JSON Serialization:**
- Use for: Domain events, domain commands, internal messaging
- Benefits: Human-readable, easy debugging
- Location: `{service}-application/.../integration/`

**Protobuf Serialization:**
- Use for: Redis streams, external integration, performance-critical paths
- Benefits: Compact, efficient, language-agnostic
- Location: `{service}-application/.../integration/`

## Naming Conventions

- **Channel**: `{applicationIdentifier}.persistence.{event-type}.channel`
- **Storage**: `{applicationIdentifier}.persistence.{event-type}.storage`
- **Bean**: `jsonRedisChannelMessageStore` / `protoRedisChannelMessageStore`



