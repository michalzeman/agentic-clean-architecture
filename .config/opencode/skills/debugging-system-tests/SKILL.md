---
name: debugging-system-tests
description: Use when system tests fail in a multi-service distributed system with message brokers, and the failure involves missing events, 400 BadRequest, or service startup errors after profile changes.
---

# Debugging System Tests in Clean Architecture Bank Demo

## Overview

System test failures in distributed services are rarely about the test itself — they're about event flow. Trace the data, not the error.

**Core principle:** Follow events from their origin through every layer until you find where they disappear.

## When to Use

Use when system tests fail with:
- `WebClientResponseException$BadRequest` (HTTP 400) on transaction creation
- `AssertionError` waiting for events that never arrive
- `UnsatisfiedDependencyException` or `NoSuchBeanDefinitionException` on startup
- `JetStreamConsumeException` or `[SUB-90007] No matching streams`
- Tests timing out after 5+ minutes
- Skipped tests due to initialization errors

**When NOT to use:** For pure unit test failures or compilation errors.

## Quick Reference

| Step | Action | Why |
|------|--------|-----|
| 1 | Run with profile | `./gradlew systemTest -PspringProfileGroup=postgres-nats` |
| 2 | Check container health | `docker ps` + `docker logs <container>` |
| 3 | Check service startup logs | Profile activation, stream creation, consumer startup |
| 4 | Verify stream initialization | Look for `JetStreamStreamInitializer completed: N streams` |
| 5 | Trace event publishing | Domain event listener → outbound channel → stream |
| 6 | Trace event consumption | Stream → consumer bean → inbound channel → domain command |
| 7 | Verify read model state | AccountView / transaction state in service logs |

## Step-by-Step Debugging Flow

### Phase 1: Reproduce and Observe

```bash
# Run system tests for a specific profile
./gradlew systemTest -PspringProfileGroup=postgres-nats
```

**Check container health immediately:**
```bash
docker ps --format "table {{.Names}}\t{{.Status}}"
docker logs bank-account-service --tail=50
docker logs bank-transaction-service --tail=50
```

**Look for these startup signals:**
- `The following N profiles are active:` — confirm expected profiles loaded
- `JetStreamStreamInitializer completed: N streams configured` — streams exist
- `inbound*EventsFlow` — consumer flows registered
- `ServiceActivator` / `handleOutboundEvent` — outbound event handlers active
- `Exception`, `ERROR`, `FAILED` — any startup failures

### Phase 2: Identify Service Modules

**REQUIRED:** Use the `project-structure` skill to map modules.

For this project, service modules follow the pattern:
```
bank-{service}/
├── bank-{service}-domain/
├── bank-{service}-application/
├── bank-{service}-adapter-redis-stream/
├── bank-{service}-adapter-nats-jetstream/
├── bank-{service}-adapter-postgresql/
├── bank-{service}-contract-proto/
└── bank-{service}-boot-app/
```

### Phase 3: Trace Data Flow

Events flow through 7 layers. Check each:

1. **Domain Event emitted:**
   - Check aggregate emits event (e.g., `BankAccountAggregate.withdraw()`)
   - Event has `@DomainEvents` annotation

2. **Domain Listener picks it up:**
   - File: `*DomainEventListener.kt` (e.g., `BankAccountDomainEventListener.kt`)
   - Profile check: Is listener active under current profile?
   - `@EventListener` or `@ServiceActivator` on correct channel

3. **Outbound channel exists:**
   - Check `JetStreamChannelConfiguration.kt` or `*RedisStreamConfiguration.kt`
   - Channel bean must be `PublishSubscribe` for domain events

4. **Outbound config publishes:**
   - File: `Outbound*Configuration.kt`
   - `handleOutboundEvent` method with `@ServiceActivator`
   - `@Profile` annotation — CRITICAL: must be active in current profile

5. **Message broker has the stream:**
   - Redis: `XINFO STREAM <stream>` inside redis container
   - NATS: `nats stream info <stream>` or check `JetStreamStreamInitializer` logs
   - Subject must match between producer and consumer

6. **Inbound consumer subscribes:**
   - File: `Inbound*Configuration.kt`
   - Consumer bean with correct `streamName`, `subject`, `consumerName`
   - `@Profile` annotation — must be active in current profile
   - Converter bean exists and maps protobuf correctly

7. **Application handler processes:**
   - File: `Inbound*EventsIntegrationConf.kt` or `Inbound*EventConf.kt`
   - Channel receives message → transforms to command → sends to command channel
   - Command handler exists in application layer

### Phase 4: Check Configuration Asymmetry

The most common root cause in this project: **asymmetric configuration between services.**

**Check these pairs match:**

| Bank-Account Service | Bank-Transaction Service | Check |
|----------------------|--------------------------|-------|
| `application-nats-jetstream.yml` | `application-nats-jetstream.yml` | Both define all needed streams |
| `BankAccountJetStreamProperties` | `BankTransactionJetStreamProperties` | Same property count (6 each) |
| `InboundJetStreamConfiguration` | `InboundJetStreamConfiguration` | Both exist |
| outbound publishes `bank-account.events` | inbound consumes `bank-account.events` | Subject matches |
| outbound publishes `bank-transaction.events` | inbound consumes `bank-transaction.events` | Subject matches |
| consumer name = `bank-account-consumer` | consumer name = `bank-transaction-consumer` | Different names OK |

**YAML files to compare:**
- `bank-account-adapter-nats-jetstream/src/main/resources/application-nats-jetstream.yml`
- `bank-transaction-adapter-nats-jetstream/src/main/resources/application-nats-jetstream.yml`
- `bank-account-boot-app/src/main/resources/application-nats-jetstream.yml`
- `bank-transaction-boot-app/src/main/resources/application-nats-jetstream.yml`

### Phase 5: Verify Read Model State

If transaction creation returns HTTP 400:
1. Bank-transaction service queries `AccountView` read model
2. `AccountView` is populated by `BankAccountEvent` consumer
3. If consumer not receiving events → `AccountView` empty → 400 BadRequest

**Verify in bank-transaction-service logs:**
- `AccountCreatedEvent` received?
- `AccountView` updated?
- Query for account ID returns result?

### Phase 6: Check Profile-Specific Beans

Spring profiles control which beans activate. Common mistake:

| Bean | Profile | Missing Profile Symptom |
|------|---------|------------------------|
| `RedisStreamBankAccountEventsPublisher` | `!nats-jetstream` | Bean missing when Redis expected |
| `JetStreamBankAccountEventsPublisher` | `nats-jetstream` | Bean missing when NATS expected |
| `InboundBankAccountRedisStreamConfiguration` | none (always on) | Conflicts with JetStream inbound |

**Check `BankSystemTestConfiguration.kt` for test-specific beans:**
- Test consumers may conflict with service consumers
- Different `consumerName` values may cause message stealing (WORKQUEUE retention)
- Test beans are `@ConditionalOnProperty(nats.jetstream.enabled=true)`

## Common Failures and Fixes

| Symptom | Likely Cause | Fix |
|---------|-------------|-----|
| `[SUB-90007] No matching streams` | Stream not created | Add stream to `application-nats-jetstream.yml` in adapter |
| `0 streams configured` | `nats.jetstream.streams` missing | Define streams in YAML |
| `MissingBean` for publisher | `@Component` without `@Profile` | Add `@Profile("!nats-jetstream")` to Redis publisher |
| HTTP 400 on POST transaction | Account not in read model | Check inbound consumer for `BankAccountEvent` |
| Test timeout 5m+ | Event never arrives | Check consumer name != test consumer name (WORKQUEUE stealing) |
| `UnsatisfiedDependencyException` | Bean created but dependencies missing | Check config class `@Profile` vs component `@Component` profile |

## Red Flags

- Skipping container log checks
- Assuming "stream should exist" without verifying `JetStreamStreamInitializer` output
- Comparing only one service's config (always compare BOTH services)
- Changing code before tracing the full data flow
- Assuming `@Profile` on config class protects `@Component` beans

## Commands Reference

```bash
# Run tests
./gradlew systemTest -PspringProfileGroup=postgres-nats

# Container logs
docker logs bank-account-service --tail=100 | grep -E "(ERROR|Exception|profile|stream|consumer)"
docker logs bank-transaction-service --tail=100 | grep -E "(ERROR|Exception|profile|stream|consumer)"

# Check NATS streams
docker exec -it <nats-container> nats stream info bank-account-events

# Check Redis streams
docker exec -it <redis-container> redis-cli XINFO STREAM bank-account.events

# Build single module
./gradlew :bank-system-tests:test --tests "TransactionJetStreamIntegrationSystemTest"
```

## Related Skills

- **REQUIRED:** `project-structure` — to identify service modules and layers
- **REQUIRED:** `systematic-debugging` — for root cause analysis methodology
- **RECOMMENDED:** `verification-before-completion` — to verify fixes before claiming success
