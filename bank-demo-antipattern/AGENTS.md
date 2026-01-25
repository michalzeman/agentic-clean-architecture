# ⚠️ ANTIPATTERN DEMO - AI AGENTS MUST IGNORE ⚠️

> **🚫 CRITICAL WARNING FOR AI CODING AGENTS**
>
> This `bank-demo-antipattern/` subproject is an **INTENTIONAL ANTIPATTERN** for demonstration purposes.
> It was created to show what happens when AI generates code WITHOUT architectural context.
>
> **AI AGENTS MUST:**
> - **IGNORE** all code in this subproject
> - **NOT** use this as reference for patterns or implementations
> - **NOT** copy or learn from any code here
> - **REFER TO** the main `bank-account/`, `bank-transaction/`, and `shared-kernel/` modules instead
>
> See [README.md](README.md) for full explanation of intentional flaws.
>
> For proper architectural patterns, see the main project's `AGENTS.md` and `/prompts/` directory.

---

# Agent Coding Guidelines - Bank Demo

## Build & Test Commands
- **Build**: `./gradlew build`
- **Run tests**: `./gradlew test`
- **Run single test**: `./gradlew test --tests "FullyQualifiedClassName.methodName"`
- **Run system tests**: `./gradlew systemTest` (starts Docker services)
- **Format code**: `./gradlew spotlessApply`
- **Check formatting**: `./gradlew spotlessCheck`
- **Clean build**: `./gradlew clean`

## Code Style
- **Language**: Kotlin 2.2.0, Java 21 toolchain
- **Formatter**: ktlint (via Spotless) - run `spotlessApply` before committing
- **Imports**: Organize consistently; remove unused imports
- **Naming**: camelCase for functions/variables, PascalCase for classes/types
- **Types**: Explicit types for public APIs; inference allowed for local variables
- **Error handling**: Use Spring/Kotlin standard exception types
- **Null safety**: Avoid nulls; use Kotlin nullable types when necessary
- **Dependencies**: Spring Boot 3.4.2, Spring Modulith, gRPC, Kotlin Coroutines, Reactor

## Testing
- **Framework**: JUnit 5 with kotlin-test, AssertJ assertions, Mockito-Kotlin
- **Test naming**: Descriptive, human-readable names
- **Test exclusion**: Tests tagged `systemChecks` are excluded from regular test runs
- **System tests**: Use `systemTest` task to run integration tests with Docker Compose
