# { Domain name }Aggregate Documentation

This document describes the `{ Domain name }Aggregate` and its complete communication flow from the domain layer through the database layer within the { service name } service.

## Project Structure and Module Separation

The { service name } service follows a modular structure with the following key modules:

```
{ service name }/
├── { service name }-domain/          # Domain layer containing aggregates and domain events
│   └── src/main/kotlin/mz/data/platform/job/source/domain/{ domain name }/
│       ├── { Domain name }.kt             # Aggregate root entity
│       ├── { Domain name }Aggregate.kt    # Main aggregate implementation
│       ├── { Domain name }DomainCommand.kt # Domain commands
│       └── { Domain name }DomainEvent.kt   # Domain events
├── { service name }-adapter-file/    # File adapter for persistence operations
│   └── src/main/kotlin/mz/data/platform/job/source/adapter/file/
│       └── Redis{ Domain name }Repository.kt # Repository for file-based persistence
│       └── Redis{ Domain name }.kt           # Spring Data Persistence entity mapped to DB row e.g. @RedisHash
│       └── { Domain name }Repository.kt      # Spring Data Repository interface e.g. for RedisDB
├── { service name }-application/     # Application layer containing integration configurations
|   |---src/main/mz/data/platform/job/
|       |--{ Domain name }CommandHandler.kt # application API for the interacting domain aggergate root 
|       |--{ Domain name }DomainEventListener.kt # Spring event @EventListener for handling @DomainEvents  
|       |--{ Domain name }Repository.kt # Adapter port interface for accessing into the Persistence e.g. Redis DB  
├── { service name }-adapter-redis-stream/ # Redis stream adapter for publishing domain events
└── { service name }-boot-app/        # Boot application with main entry point
```


## Communication Contract

### Domain Commands
The `{ Domain name }Aggregate` processes the following domain commands:

- `Create{ Domain name }`: Creates a new { domain name } with initial status PENDING
- `Update{ Domain name }FilePosition`: Updates the file position of a { domain name }
- `Update{ Domain name }Status`: Updates the status of a { domain name }

### Domain Events
The aggregate produces the following domain events:

- `{ Domain name }Created`: Generated when a new { domain name } is created
- `{ Domain name }StatusUpdated`: Generated when { domain name } status changes
- `{ Domain name }PositionUpdated`: Generated when { domain name } file position changes

## Aggregate Implementation

The `{ Domain name }Aggregate` is implemented in `{ Domain name }Aggregate.kt` and follows these key patterns:
- domain entity (Clean Architecture)
- domainEvents list to track changes
  - empty list → no update
  - list contains domain events represents how the root aggregate changed

### Aggregate Abstraction

```kotlin
data class { Domain name }Aggregate(
    val { Domain name attribute }: { Domain name },
    val domainEvents: List<{ Domain name }Event> = emptyList(),
) {
    // create, update ...

    // Example of creation
    companion object {
        fun create(cmd: { domain }Command.CreateJob): { domain }Aggregate {
            val domain =
                Job(
                    aggregateId = AggregateId(UUID.randomUUID().toString()),
                    correlationId = cmd.correlationId,
                    details = cmd.details,
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                    status = JobStatus.SCHEDULED,
                    version = 0,
                )
    
            val jobCreated =
                JobDomainEvent.JobCreated(
                    job = job,
                    aggregateId = job.aggregateId,
                )
    
            return JobAggregate(
                job = job,
                domainEvents = listOf(jobCreated),
            )
        }
    }   
}
```

### Creation
```kotlin
fun create(cmd: Create{ Domain name }): { Domain name }Aggregate {
    val aggregateId = AggregateId(UUID.randomUUID().toString())
    val now = Instant.now()
    val { domain name } = { Domain name }(
        aggregateId = aggregateId,
        jobId = cmd.jobId,
        details = cmd.details,
        createdAt = now,
        updatedAt = now,
        status = JobStatus.PENDING,
        version = 0,
    )
    val event = { Domain name }DomainEvent.{ Domain name }Created({ domain name })
    return { Domain name }Aggregate({ domain name }, listOf(event))
}
```


### State Updates
The aggregate handles updates through methods:
1. `update(cmd: Update{ Domain name }FilePosition)` - Updates file position and status
2. `update(cmd: Update{ Domain name }Status)` - Updates worker status directly

Each update method:
- Increments the version number
- Updates timestamps
- Generates appropriate domain events
- Returns a new aggregate instance with updated state

## Database Layer Integration

The aggregate interacts with the database through a complete flow:

### 1. Aggregate State Retrieval

Actions are handled via DomainCommandHandler located in the `{ service name }-application`.
The aggregate retrieves its state from the database through the repository pattern:

- Domain Command Handler:
```kotlin
@Component
class JobCommandHandler(
    private val jobRepository: JobRepository,
    private val lockProvider: LockProvider,
) {
    suspend fun handle(command: JobDomainCommand): Job =
        when (command) {
            is JobDomainCommand.CreateJob ->
                lockProvider.withLock(
                    command.correlationId,
                ) { jobRepository.upsert(JobAggregate.create(command)) }
            is JobDomainCommand.UpdateJobStatus ->
                lockProvider.withLock(command.aggregateId.value) {
                    jobRepository
                        .findById(command.aggregateId)
                        ?.let { JobAggregate(it).update(command) }
                        ?.let { jobRepository.upsert(it) }
                        ?: throw IllegalArgumentException("Job with id ${command.aggregateId} not found")
                }
// ... more commands
        }
}
```

- interface example:

```kotlin
// Repository interface in { service name }-adapter-file module
interface { Domain name }Repository {
    suspend fun findById(aggregateId: AggregateId): { Domain name }?
    suspend fun upsert({ domain name }: { Domain name }Aggregate): { Domain name }
}
```

- implementation example for Job { domain name }:
```kotlin
@Component
internal class RedisJobRepository(
    private val repository: JobDataRepository,
) : JobRepository {
    override suspend fun upsert(jobAggregate: JobAggregate): Job =
        withContext(Dispatchers.IO) {
            val job = jobAggregate.toRedisJob()
            repository.save(job).toJob()
        }

    override suspend fun findById(aggregateId: AggregateId): Job? =
        withContext(Dispatchers.IO) {
            repository
                .findById(UUID.fromString(aggregateId.value))
                .map { it.toJob() }
                .getOrNull()
        }

    override suspend fun findByJobWorkerId(jobWorkerId: AggregateId): Job? =
        withContext(Dispatchers.IO) {
            repository
                .findByJobWorkerId(jobWorkerId.value)
                .map { it.toJob() }
                .getOrNull()
        }
}
```

#### Persistence
- spring data redis
- persistence entity for the example of Job domain:
```kotlin
@RedisHash("Job")
internal open class RedisJob(
    @field:Id val id: UUID = UUID.randomUUID(),
    val details: JobDetails,
    val createdAt: Instant,
    val updatedAt: Instant,
    val status: JobStatus,
    @field:Version val version: Long = 0,
    @field:Indexed val jobParamId: String?,
    val jobWorkerVersion: Long?,
) {
    @Transient var domainEvents: MutableList<JobDomainEvent> = mutableListOf()

    @DomainEvents
    open fun domainEvents(): Collection<JobDomainEvent> =
        if (domainEvents.isEmpty()) {
            listOf(JobDomainEvent.JobCreated(job = this.toJob(), aggregateId = AggregateId(id.toString())))
        } else {
            domainEvents
        }

    @AfterDomainEventPublication
    open fun callbackMethod() {
        domainEvents.clear()
    }
}
```

### 2. Aggregate State Management
The `{ Domain name }Aggregate` manages state by:
- Loading current state from database via repository
- Applying domain commands to update state
- Saving updated state back to database

### 3. Persistence Flow
1. **Domain Layer**: `{ Domain name }Aggregate` manages business logic and generates domain events
2. **Application Layer**: Aggregate state is retrieved from database via repository
3. **Repository Layer**: `BackedSourceFileRepository` handles database operations
4. **Persistence Layer**: Database stores the aggregate state and domain events

### 4. Event Publishing Flow
Following the established patterns in the data-job-orchestrator service, domain events from `{ Domain name }Aggregate` are published through:

1. **Persistent Message Channels**: Using Redis-backed queue channels with `RedisChannelMessageStore`
2. **PublishSubscribe Pattern**: Ensures scalability for multiple consumers
3. **Redis Stream Publisher**: Converts domain events to protobuf messages and publishes to Redis streams

This approach ensures reliable message publishing while maintaining loose coupling between components and enabling future scalability.

## Complete Data Flow

```
[External Request] → [Domain Command Handler] → [{ Domain name }Aggregate]
                            ↓
                   [Repository (Database)] ← [{ Domain name }]
                            ↓
                   [Domain Events] → [Redis Stream Publisher]
                            ↓
                   [Event Consumers] → [Database Updates]
```


The aggregate pattern ensures that business logic is encapsulated within the aggregate while maintaining proper separation of concerns between domain logic, persistence, and event publishing.