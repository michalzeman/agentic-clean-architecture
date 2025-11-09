# Step-by-Step Prompt: Adding Redis Stream Producer for Domain Events

## Overview
This guide provides a systematic approach to add a Redis stream producer for aggregate domain events (e.g., `JobWorkerAggregate`) following Clean Architecture patterns.

## Prerequisites
- Service with modules: domain, application, adapter-redis-stream, boot-app
- Redis and protobuf contracts configured
- Spring Integration dependencies in place

## Step 1: Define Protobuf Contract

### 1.1 Add Message Definitions
Location: `{service}-contract-proto/src/main/proto/`

```protobuf
syntax = "proto3";

message ProtoJobWorkerEvent {
  string aggregate_id = 1;
  int32 version = 2;
  oneof event {
    ProtoJobWorkerCreated job_worker_created = 3;
    ProtoJobWorkerStatusUpdated job_worker_status_updated = 4;
    ProtoJobWorkerPositionUpdated job_worker_position_updated = 5;
  }
}

message ProtoJobWorkerCreated {
  string job_id = 1;
  string status = 2;
}

message ProtoJobWorkerStatusUpdated {
  string status = 1;
}

message ProtoJobWorkerPositionUpdated {
  int64 file_line_number = 1;
}
```

### 1.2 Build Project
Run gradle build to generate Kotlin classes.

## Step 2: Application Layer (application module)

### 2.1 Create Integration Configuration

Location: `{service}-application/src/main/kotlin/.../integration/OutboundJobWorkerEventsIntegrationConf.kt`

```kotlin
@Configuration
class OutboundJobWorkerEventsIntegrationConf(
    @Value("\${application.identifier}") private val applicationIdentifier: String,
    private val jsonRedisChannelMessageStore: RedisChannelMessageStore,
) {
    @Bean
    fun jobWorkerDomainEventsChannel() =
        MessageChannels
            .queue(
                "$applicationIdentifier.persistence.job-worker-events.channel",
                jsonRedisChannelMessageStore,
                "$applicationIdentifier.persistence.job-worker-events.storage",
            ).apply {
                datatype(JobWorkerDomainEvent::class.java)
            }

    @Bean
    fun jobWorkerDomainEventsPublishSubscribeChannel() =
        MessageChannels.publishSubscribe(true)

    @Bean
    fun jobWorkerEventsDistributionFlow(
        jobWorkerDomainEventsChannel: MessageChannel,
        jobWorkerDomainEventsPublishSubscribeChannel: MessageChannel,
    ) = IntegrationFlow
        .from(jobWorkerDomainEventsChannel)
        .channel(jobWorkerDomainEventsPublishSubscribeChannel)
        .get()
}
```

## Step 3: Configure Redis Stream (adapter-redis-stream module)

### 3.1 Update Properties

Location: `{service}-adapter-redis-stream/src/main/kotlin/.../ServiceRedisStreamProperties.kt`

```kotlin
data class ServiceRedisStreamProperties(
    val jobWorkerEventsStream: String,
)
```

### 3.2 Update application.yml

```yaml
adapters:
  redis:
    stream:
      job-worker-events-stream: "job-worker-events"
```

## Step 4: Implement Redis Publisher (adapter-redis-stream module)

Location: `{service}-adapter-redis-stream/src/main/kotlin/.../outbound/RedisStreamJobWorkerEventsPublisher.kt`

```kotlin
@Component
class RedisStreamJobWorkerEventsPublisher(
    private val jobWorkerEventPublisher: RedisStreamPublisher<ProtoJobWorkerEvent>,
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @ServiceActivator(
        inputChannel = "jobWorkerDomainEventsPublishSubscribeChannel",
        requiresReply = "false",
        async = "true",
    )
    suspend fun publish(event: Message<JobWorkerDomainEvent>) {
        val protoEvent = event.payload.mapToProto()
        val message = MessageBuilder
            .withPayload(protoEvent)
            .copyHeaders(event.headers)
            .setHeaderIfAbsent(AGGREGATE_ID, event.payload.aggregateId.value)
            .setHeader(VERSION, event.payload.version)
            .build()

        logger.info("Publishing JobWorkerEvent: ${event.payload.aggregateId}")
        jobWorkerEventPublisher.publish(message)
    }
}

internal fun JobWorkerDomainEvent.mapToProto(): ProtoJobWorkerEvent =
    when (this) {
        is JobWorkerDomainEvent.JobWorkerCreated ->
            ProtoJobWorkerEvent.newBuilder()
                .setAggregateId(jobWorker.aggregateId.value)
                .setVersion(jobWorker.version)
                .setJobWorkerCreated(
                    ProtoJobWorkerCreated.newBuilder()
                        .setJobId(jobWorker.jobId.value)
                        .setStatus(jobWorker.status.name)
                        .build()
                )
                .build()
        is JobWorkerDomainEvent.JobWorkerStatusUpdated ->
            ProtoJobWorkerEvent.newBuilder()
                .setAggregateId(aggregateId.value)
                .setVersion(version)
                .setJobWorkerStatusUpdated(
                    ProtoJobWorkerStatusUpdated.newBuilder()
                        .setStatus(jobStatus.name)
                        .build()
                )
                .build()
        is JobWorkerDomainEvent.JobWorkerPositionUpdated ->
            ProtoJobWorkerEvent.newBuilder()
                .setAggregateId(aggregateId.value)
                .setVersion(version)
                .setJobWorkerPositionUpdated(
                    ProtoJobWorkerPositionUpdated.newBuilder()
                        .setFileLineNumber(fileLineNumber)
                        .build()
                )
                .build()
    }
```

## Step 5: Wiring Configuration (adapter-redis-stream module)

Location: `{service}-adapter-redis-stream/src/main/kotlin/.../OutboundServiceRedisStreamConfiguration.kt`

```kotlin
@Bean
fun jobWorkerEventPublisher(): RedisStreamPublisher<ProtoJobWorkerEvent> {
    val serializer = RedisMapRecordJsonSerializer(objectMapper)
    return RedisStreamPublisher(
        streamKey = properties.jobWorkerEventsStream,
        reactiveRedisTemplate,
        serializer,
    )
}
```

## File Structure

```
data-job-source/
├── data-job-source-application/
│   └── src/main/kotlin/.../integration/
│       └── OutboundJobWorkerEventsIntegrationConf.kt
├── data-job-source-adapter-redis-stream/
│   └── src/main/kotlin/.../
│       ├── outbound/
│       │   └── RedisStreamJobWorkerEventsPublisher.kt
│       └── wiring/
│           └── OutboundServiceRedisStreamConfiguration.kt
└── data-job-source-boot-app/
    └── src/main/resources/
        └── application.yml
```

## Key Points

1. **Domain Events** → **Protobuf Contract** → **Redis Stream**
2. Use `PublishSubscribeChannel` for fanout to multiple consumers
3. Each consumer gets its own resilient queue
4. Map domain events to protobuf with aggregate metadata