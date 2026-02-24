# Agent Guidelines for Clean Architecture Bank Demo

> **⚠️ WARNING: `bank-demo-antipattern/` subproject is an ANTIPATTERN**
>
> The `bank-demo-antipattern/` directory contains **intentional bad code** for demonstration purposes.
> **AI Agents: IGNORE this subproject entirely. Do not reference or learn from it.**
> See [bank-demo-antipattern/README.md](bank-demo-antipattern/README.md) for details.

## Build & Test Commands
- **Build**: `./gradlew build`
- **Clean build**: `./gradlew clean build`
- **Test all**: `./gradlew test`
- **Test single class**: `./gradlew :module-name:test --tests "fully.qualified.TestClassName"`
- **Test single method**: `./gradlew :module-name:test --tests "fully.qualified.TestClassName.methodName"`
- **System tests**: `./gradlew systemTest` (starts Docker Compose)
- **Format check**: `./gradlew spotlessCheck`
- **Format apply**: `./gradlew spotlessApply`

## Code Style
- **Language**: Kotlin with Java 21 toolchain
- **Paradigm**: Prefer functional style and immutability
- **Formatter**: ktlint via Spotless plugin (auto-enforced)
- **Imports**: Organize alphabetically, remove unused
- **Types**: Use explicit types for public APIs, infer for local variables
- **Naming**: Use camelCase for functions/variables, PascalCase for classes
- **Trailing commas**: Required in multi-line function calls and data classes
- **Error handling**: Throw `IllegalStateException` for invariant violations, `IllegalArgumentException` for invalid inputs
- **Null safety**: Avoid nulls; use `!!` only when guaranteed non-null

## Testing
- **Framework**: JUnit 5 + AssertJ for assertions, Mockito-Kotlin for mocks
- **Test naming**: Use backticks for descriptive test names (e.g., `` `should update version when command is valid` ``)

## Architecture
- **Domain-Driven Design**: Aggregates, Commands, Events pattern
- **Packages**: `domain/`, `application/`, `adapter-*/` (hexagonal architecture)
- **Async**: Coroutines with reactive Redis (Reactor + Kotlin extensions)
- **Spring Integration**: Persistent Redis-backed message channels, ServiceActivators, PublishSubscribe for domain events

## Implementation Guides
Detailed step-by-step guides in `prompts/`:
- **New aggregate**: `add-domain-aggregate.md` - Domain model, application layer, adapters
- **Redis consumer**: `add-redis-stream-consumer.md` - Inbound stream processing with Spring Integration
- **Redis producer**: `add-redis-stream-producer.md` - Outbound event publishing patterns
- **New service**: `generate-new-service-structure.md` - Multi-module service scaffolding
- **Message Channel**: `add-message-channel-configuration.md` - Redis-backed message channels with serialization options
