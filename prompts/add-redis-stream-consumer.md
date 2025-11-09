# Step-by-Step Prompt: Adding New Redis Stream Consumer with Spring Integration

## Overview
This guide provides a systematic approach to add a new Redis stream consumer to the data-job-orchestrator service, following the existing Clean Architecture patterns and Spring Integration conventions.

## Prerequisites
- Existing service structure with modules: domain, application, adapter-redis-stream, boot-app
- Redis connection factory configured
- Protobuf contracts already defined and dependencies added
- Spring Integration and Redis Stream dependencies in place
- RedisChannelMessageStore configured for persistent message channels

## Step 1: Update Application Layer (application module)

### 1.1 Define Application Events/Commands
- Create application-level events that bridge domain and infrastructure
- Location: `data-job-orchestrator-application/src/main/kotlin/.../application/`
- Example: `NewStreamEvent.kt`, `NewStreamCommand.kt`

### 1.2 Create/Update Application Services
- Add service methods to handle the new stream events
- Implement business logic orchestration
- Handle domain event publishing if needed

### 1.3 Create Integration Configuration
- Create integration configuration for the new event type
- Location: `data-job-orchestrator-application/src/main/kotlin/.../integration/`
- Example structure:
  ```kotlin
  @Configuration
  class InboundNewEventIntegrationConf(
      private val jsonRedisChannelMessageStore: RedisChannelMessageStore,
      @Value("\${application.identifier}") private val applicationIdentifier: String,
  ) {
      @Bean
      fun inboundNewEventChannel() =
          MessageChannels
              .queue(
                  "$applicationIdentifier.inbound.new-event.channel",
                  jsonRedisChannelMessageStore,
                  "$applicationIdentifier.inbound.new-event.storage",
              ).apply {
                  datatype(NewStreamEvent::class.java)
              }
  }
  ```

### 1.4 Create Use Case Handler
- Create a use case handler that processes the events from the message channel
- Location: `data-job-orchestrator-application/src/main/kotlin/.../application/`
- Use `@ServiceActivator` annotation
- Example:
  ```kotlin
  @Component
  class NewEventToEntityUseCase(
      private val entityCommandHandler: YourEntityCommandHandler,
      private val entityRepository: YourEntityRepository,
  ) {
      @ServiceActivator(
          inputChannel = "inboundNewEventChannel",
          requiresReply = "false",
          async = "true",
      )
      suspend operator fun invoke(
          event: NewStreamEvent,
          @Headers headers: MessageHeaders,
      ) {
          logger.info("Processing NewStreamEvent: $event, headers: $headers")
          // Process the event and execute business logic
          entityCommandHandler.handle(/* domain command based on event */)
      }
  }
  ```

## Step 2: Configure Redis Stream Properties (adapter-redis-stream module)

### 2.1 Update Properties Class
- File: `DataJobOrchestratorRedisStreamProperties.kt`
- Add new stream configuration properties:
  ```kotlin
  val newEventStream: String,
  val newEventConsumerName: String,
  // Add any additional consumer-specific properties
  ```

### 2.2 Update application.yml/properties
- Add configuration values for the new stream:
  ```yaml
  adapters:
    redis:
      stream:
        new-event-stream: "your-stream-name"
        new-event-consumer-name: "your-consumer-name"
        consumer-group: "your-consumer-group"
  ```

## Step 3: Create Message Mapper (adapter-redis-stream module)

### 3.1 Implement Event Mapper
- Create: `New{Event}Mapper.kt` in `inbound/` package
- Location: `adapter-redis-stream/src/main/kotlin/.../inbound/`
- Map from protobuf messages to application events/entities
- Example structure:
  ```kotlin
  @Component
  internal class NewEventMapper {
      internal fun mapNewEvent(protoEvent: ProtoNewEvent): ApplicationNewEvent {
          // Map protobuf event to application event/entity
          return ApplicationNewEvent(
              // mapping logic here
          )
      }
  }
  ```

## Step 4: Configure Spring Integration Flow (adapter-redis-stream module)

### 4.1 Update/Create Inbound Configuration
- File: `InboundDataJobOrchestratorRedisStreamConfiguration.kt`
- Add new consumer bean configuration:
  ```kotlin
  @Bean
  internal fun newEventStreamConsumer(
      inboundNewEventChannel: MessageChannel,
      newEventMapper: NewEventMapper,
  ): RedisStreamConsumer<ProtoNewEvent, ApplicationNewEvent> {
      val consumerGroup =
          Consumer.from(
              dataJobOrchestratorRedisStreamProperties.consumerGroup,
              dataJobOrchestratorRedisStreamProperties.newEventConsumerName,
          )

      val builder =
          RedisSteamConsumerBuilder<ProtoNewEvent, ApplicationNewEvent>(
              dataJobOrchestratorRedisStreamProperties.newEventStream,
              inboundNewEventChannel,
              consumerGroup.group,
              consumerGroup.name,
              ProtoNewEvent::class.java,
              redisConnectionFactory,
              objectMapper,
          )

      return builder
          .withMapper(newEventMapper::mapNewEvent)
          .withLockProvider(lockProvider)
          .build()
  }
  ```

## Step 5: Update Dependencies (build.gradle.kts files)

### 5.1 Redis Stream Adapter Dependencies
- Ensure all required dependencies are included:
  - Spring Integration Core
  - Spring Integration Redis
  - Redis connection factory
  - Application and domain modules
  - Protobuf contract modules

### 5.2 Boot App Dependencies
- Add dependency to redis-stream adapter module
- Verify all transitive dependencies are resolved

## Step 6: Configure Boot Application (boot-app module)

### 6.1 Update Application Configuration
- Enable configuration properties
- Ensure component scanning includes new packages
- Configure Redis connection properties

### 6.2 Update application.yml
- Add Redis stream configuration
- Configure consumer group settings

## Step 7: Add Tests

### 7.1 Unit Tests
- Test message mappers with various input scenarios
- Test use case handlers with mock dependencies
- Mock dependencies appropriately

### 7.2 Integration Tests
- Test end-to-end stream consumption
- Verify error handling and retry mechanisms
- Test with embedded Redis or test containers

### 7.3 System Tests
- Test complete flow from Redis stream to business logic
- Verify message persistence in Redis-backed channels

## Example File Structure After Implementation

```
data-job-orchestrator/
├── data-job-orchestrator-domain/
│   └── src/main/kotlin/.../domain/
│       └── NewDomainEvent.kt
├── data-job-orchestrator-application/
│   └── src/main/kotlin/.../application/
│       ├── NewApplicationEvent.kt
│       ├── NewEventToEntityUseCase.kt
│       └── integration/
│           └── InboundNewEventIntegrationConf.kt
├── data-job-orchestrator-adapter-redis-stream/
│   └── src/main/kotlin/.../adapter/redis/stream/
│       ├── inbound/
│       │   └── NewEventMapper.kt
│       └── wiring/
│           ├── InboundDataJobOrchestratorRedisStreamConfiguration.kt
│           └── DataJobOrchestratorRedisStreamProperties.kt
└── data-job-orchestrator-boot-app/
    └── src/main/resources/
        └── application.yml
```

## Key Patterns to Follow

1. **Clean Architecture**: Keep domain logic pure, use adapters for external concerns
2. **Persistent Message Channels**: Use Redis-backed queue channels for reliability
3. **Spring Integration**: Use message channels, service activators, and integration flows
4. **Configuration Properties**: Externalize all stream-related configurations
5. **Error Handling**: Implement proper error handling and retry mechanisms
6. **Testing**: Write comprehensive tests at all layers
7. **Use Case Pattern**: Event handlers are use cases in the application layer that orchestrate business logic

This step-by-step approach ensures consistent implementation following the existing patterns in the data-job-orchestrator service.
