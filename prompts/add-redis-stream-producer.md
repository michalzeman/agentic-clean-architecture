# Step-by-Step Prompt: Adding New Redis Stream Producer with Spring Integration

## Overview
This guide provides a systematic approach to add a new Redis stream producer to a service following the Clean Architecture patterns and Spring Integration conventions used in data-job-orchestrator.

## Prerequisites
- Service structure with modules: domain, application, adapter-redis-stream, boot-app
- Redis connection factory configured
- Protobuf contracts defined
- Spring Integration and Redis Stream dependencies in place
- RedisChannelMessageStore configured for persistent message channels

## Two Approaches for Redis Stream Producers

### Approach 1: ServiceActivator Pattern (Recommended for Domain Events)
Based on `RedisStreamJobDomainEventPublisher` - ideal for publishing domain events with transformation logic.

### Approach 2: Direct Integration Flow Pattern (Simple Commands)
Based on `OutboundDataJobOrchestratorRedisStreamConfiguration` - ideal for simple command forwarding without complex transformation.

## Step 1: Define the Message Contract

### 1.1 Update Protobuf Contract (contract-proto module)
- Add new message definitions in the appropriate .proto file
- Define the event/command structure that will be published to Redis streams
- Example location: `{service}-contract-proto/src/main/proto/`

### 1.2 Generate Protobuf Classes
- Run gradle build to generate Java/Kotlin classes from proto definitions

## Step 2: Update Application Layer (application module)

### 2.1 Create Outbound Integration Configuration
- Create integration configuration for the new producer
- Location: `{service}-application/src/main/kotlin/.../integration/`

```kotlin
@Configuration
class OutboundNewEventIntegrationConf(
    @Value("\${application.identifier}") private val applicationIdentifier: String,
    private val jsonRedisChannelMessageStore: RedisChannelMessageStore,
) {
    @Bean
    fun outboundNewEventChannel() =
        MessageChannels
            .queue(
                "$applicationIdentifier.persistence.new-event.channel",
                jsonRedisChannelMessageStore,
                "$applicationIdentifier.persistence.new-event.storage",
            ).apply {
                datatype(DomainEvent::class.java)
            }

    // Optional: Add transformation flow if needed (for Approach 2)
    @Bean
    fun outboundNewEventFlow(
        domainEventsChannel: MessageChannel,
        outboundNewEventChannel: MessageChannel,
    ) = IntegrationFlow
        .from(domainEventsChannel)
        .filter<DomainEvent> { it is DomainEvent.NewEvent }
        .transform<DomainEvent, DomainEvent.NewEvent> { it as DomainEvent.NewEvent }
        .channel(outboundNewEventChannel)
        .get()
}
```

## Step 3: Configure Redis Stream Properties (adapter-redis-stream module)

### 3.1 Update Properties Class
```kotlin
// In DataJobOrchestratorRedisStreamProperties.kt (or equivalent)
data class ServiceRedisStreamProperties(
    // ...existing properties...
    val newEventStream: String,
)
```

### 3.2 Update application.yml/properties
```yaml
adapters:
  redis:
    stream:
      new-event-stream: "your-stream-name"
```

## Step 4: Implement Publisher (Choose One Approach)

### Approach 1: ServiceActivator Pattern

#### 4.1 Create Publisher Interface (application module)
```kotlin
// In application module: outbound/NewEventPublisher.kt
interface NewEventPublisher {
    suspend fun publish(event: Message<DomainEvent>)
}
```

#### 4.2 Implement Publisher (adapter-redis-stream module)
```kotlin
// In adapter-redis-stream module: outbound/RedisStreamNewEventPublisher.kt
@Component
class RedisStreamNewEventPublisher(
    private val newEventPublisher: RedisStreamPublisher<ProtoNewEvent>,
) : NewEventPublisher {
    
    @ServiceActivator(
        inputChannel = "outboundNewEventChannel",
        requiresReply = "false",
        async = "true",
    )
    override suspend fun publish(event: Message<DomainEvent>) {
        val message = event.toMessage()
        logger.info("Publishing message: $message")
        newEventPublisher.publish(message)
    }
}

// Extension function to map domain event to protobuf message
internal fun Message<DomainEvent>.toMessage(): Message<ProtoNewEvent> {
    val eventToPublish = payload.mapToProto()
    return MessageBuilder
        .withPayload(eventToPublish)
        .copyHeaders(headers)
        .setHeaderIfAbsent(AGGREGATE_ID, payload.aggregateId.value)
        .setHeader(VERSION, payload.version)
        .build()
}

internal fun DomainEvent.mapToProto(): ProtoNewEvent =
    when (this) {
        is DomainEvent.NewEvent ->
            ProtoNewEvent.newBuilder()
                // Map your domain event fields
                .build()
        // Add other event types as needed
    }
```

### Approach 2: Direct Integration Flow Pattern

#### 4.1 Update Outbound Configuration
```kotlin
// In OutboundServiceRedisStreamConfiguration.kt
@Bean
fun newEventPublisher(): RedisStreamPublisher<ProtoNewEvent> {
    val redisMapRecordJsonSerializer = RedisMapRecordJsonSerializer(objectMapper)
    
    return RedisStreamPublisher(
        streamKey = properties.newEventStream,
        reactiveRedisTemplate,
        redisMapRecordJsonSerializer,
        lockProvider, // Optional - omit if not needed
    )
}

@Bean
fun publishNewEventFlow(
    outboundProtoNewEventChannel: MessageChannel,
    newEventPublisher: RedisStreamPublisher<ProtoNewEvent>,
) = IntegrationFlow
    .from(outboundProtoNewEventChannel)
    .handleReactive { message ->
        val messageToPublish =
            MessageBuilder
                .withPayload<ProtoNewEvent>(message.payload as ProtoNewEvent)
                .copyHeaders(message.headers)
                .build()
        mono { newEventPublisher.publish(messageToPublish) }.then()
    }
```

## Example File Structure

```
{service}/
├── {service}-application/
│   └── src/main/kotlin/.../application/
│       ├── outbound/
│       │   └── NewEventPublisher.kt (Approach 1 only)
│       └── integration/
│           └── OutboundNewEventIntegrationConf.kt
├── {service}-adapter-redis-stream/
│   └── src/main/kotlin/.../adapter/redis/stream/
│       ├── outbound/
│       │   └── RedisStreamNewEventPublisher.kt (Approach 1 only)
│       └── wiring/
│           ├── OutboundServiceRedisStreamConfiguration.kt
│           └── ServiceRedisStreamProperties.kt
└── {service}-boot-app/
    └── src/main/resources/
        └── application.yml
```

## Key Patterns to Follow

1. **Persistent Message Channels**: Use Redis-backed queue channels with RedisChannelMessageStore
2. **ServiceActivator Pattern**: Use `@ServiceActivator` for complex domain event publishing with transformation
3. **Direct Integration Flow**: Use `IntegrationFlow` with `handleReactive` for simple command forwarding
4. **Configuration Properties**: Externalize all stream-related configurations
5. **Message Headers**: Preserve and add necessary headers (AGGREGATE_ID, VERSION)
6. **Reactive Publishing**: Use `mono { }.then()` for non-blocking Redis publishing

## When to Use Which Approach

- **Approach 1 (ServiceActivator)**: When you need complex domain event transformation, logging, or business logic
- **Approach 2 (Direct Flow)**: When you need simple command forwarding without transformation logic

This approach ensures reliable message publishing following the established patterns in the data-job-orchestrator service.

## Recommended Pattern: PublishSubscribeChannel for Domain Event Distribution

### Overview
When working with persisted domain event channels (e.g., `jobDomainEventsChannel`) that represent the main channel for domain events resulting from aggregate changes, it is **strongly recommended** to integrate via a `PublishSubscribeChannel`. This pattern anticipates future scalability where multiple consumers may need to process the same domain events.

### Why Use PublishSubscribeChannel?

1. **Future-Proof Architecture**: Even if you currently have only one consumer, domain events often need to be processed by multiple consumers as the system evolves
2. **Message Resilience**: Maintains the benefits of persistent Redis-backed channels while enabling message fanout
3. **Clean Separation**: Separates the resilient storage concern from the distribution concern
4. **Non-Blocking**: Each subscriber processes messages independently without affecting others

### Implementation Pattern

Based on the `OutboundJobDomainEventsIntegrationConf` implementation:

```kotlin
@Configuration
class OutboundDomainEventsIntegrationConf(
    private val jsonRedisChannelMessageStore: RedisChannelMessageStore,
    @Value("\${application.identifier}")
    private val applicationIdentifier: String,
) {
    // Step 1: Connect persisted channel to PublishSubscribe
    @Bean 
    fun domainEventsFlow() =
        IntegrationFlow
            .from("someDomainEventsChannel") // Your persisted channel
            .channel("outboundDomainEventsPublishSubscribeChannel")
            .get()

    // Step 2: Create PublishSubscribe channel for fanout
    @Bean 
    fun outboundDomainEventsPublishSubscribeChannel() =
        MessageChannels
            .publishSubscribe(true) // Enable async processing
            .apply {
                datatype(SomeDomainEvent::class.java)
            }

    // Step 3: Create separate resilient channels for each consumer
    @Bean
    fun outboundSpecificConsumerChannel() =
        MessageChannels
            .queue(
                "$applicationIdentifier.persistence.specific-consumer.channel",
                jsonRedisChannelMessageStore,
                "$applicationIdentifier.persistence.specific-consumer.storage",
            ).apply {
                datatype(SomeDomainEvent::class.java)
            }

    // Step 4: Connect PublishSubscribe to specific consumer channels
    @Bean
    fun outboundSpecificConsumerFlow(
        outboundDomainEventsPublishSubscribeChannel: MessageChannel,
        outboundSpecificConsumerChannel: MessageChannel,
    ) = IntegrationFlow
        .from(outboundDomainEventsPublishSubscribeChannel)
        .filter<SomeDomainEvent> { /* consumer-specific filtering */ }
        .transform<SomeDomainEvent> { /* consumer-specific transformation */ }
        .channel(outboundSpecificConsumerChannel)
        .get()
}
```

### Architecture Flow

```
Domain Events → [Persisted Channel] → [PublishSubscribe] → [Consumer 1 Channel]
                                                        → [Consumer 2 Channel]
                                                        → [Consumer N Channel]
```

### Key Benefits of This Pattern

1. **Resilience**: The initial `someDomainEventsChannel` provides Redis-backed persistence
2. **Scalability**: Easy to add new consumers by subscribing to the PublishSubscribe channel
3. **Independence**: Each consumer has its own resilient queue, preventing one slow consumer from affecting others
4. **Filtering**: Each consumer can apply its own filtering and transformation logic
5. **Testability**: Easy to test individual consumer flows in isolation

### When to Apply This Pattern

- ✅ **Domain Events**: Events that represent business-significant changes to aggregates
- ✅ **Anticipated Growth**: When you expect multiple consumers in the future
- ✅ **High Reliability**: When message loss is unacceptable
- ✅ **Independent Processing**: When consumers need to process at different rates

### Anti-Pattern: Direct Multiple Consumers

❌ **Avoid** connecting multiple consumers directly to a single persistent queue:

```kotlin
// DON'T DO THIS - Multiple consumers on same queue
@ServiceActivator(inputChannel = "someDomainEventsChannel") // Consumer 1
@ServiceActivator(inputChannel = "someDomainEventsChannel") // Consumer 2 - will compete!
```

This creates a competing consumer pattern where only one consumer receives each message, which is typically not the desired behavior for domain events.

### Example: Adding a New Consumer

When adding a new consumer to an existing PublishSubscribe setup:

```kotlin
// Add new consumer channel
@Bean
fun outboundNewConsumerChannel() =
    MessageChannels.queue(
        "$applicationIdentifier.persistence.new-consumer.channel",
        jsonRedisChannelMessageStore,
        "$applicationIdentifier.persistence.new-consumer.storage",
    ).apply {
        datatype(SomeDomainEvent::class.java)
    }

// Add new consumer flow
@Bean
fun outboundNewConsumerFlow(
    outboundDomainEventsPublishSubscribeChannel: MessageChannel,
    outboundNewConsumerChannel: MessageChannel,
) = IntegrationFlow
    .from(outboundDomainEventsPublishSubscribeChannel)
    .filter<SomeDomainEvent> { it is SomeDomainEvent.SpecificType }
    .channel(outboundNewConsumerChannel)
    .get()
```

This pattern ensures that your domain event processing architecture remains resilient, scalable, and maintainable as your system evolves.
