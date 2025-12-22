# Account View Implementation in Bank-Transaction Service

## Summary

Successfully implemented an account view in the bank-transaction service that consumes account creation events from the bank-account service via Redis streams.

## Implementation Details

### 1. Domain Layer (`bank-transaction-domain`)

**Created:**
- `domain/account/AccountView.kt` - Simple read model containing only the account ID

### 2. Application Layer (`bank-transaction-application`)

**Created:**
- `application/account/AccountViewRepository.kt` - Port interface for account view persistence with `findById()` and `upsert()` methods
- `application/account/AccountCreatedEventHandler.kt` - Handler that processes `AccountCreatedEvent` and updates the local account view
- `application/integration/InboundBankAccountEventsIntegrationConf.kt` - Spring Integration configuration for inbound account events channel

### 3. Adapter Layer (`bank-transaction-adapter-redis`)

**Created:**
- `adapter/redis/account/RedisAccountView.kt` - Redis persistence entity for AccountView
- `adapter/redis/account/AccountViewDataRepository.kt` - Spring Data Redis repository interface
- `adapter/redis/account/RedisAccountViewRepository.kt` - Implementation of AccountViewRepository port

### 4. Redis Stream Adapter (`bank-transaction-adapter-redis-stream`)

**Created:**
- `adapter/redis/stream/inbound/BankAccountEventMapper.kt` - Maps protobuf `BankAccountEvent` to application `AccountCreatedEvent`
- `adapter/redis/stream/wiring/InboundBankAccountRedisStreamConfiguration.kt` - Configures Redis stream consumer bean

**Updated:**
- `BankTransactionRedisStreamProperties.kt` - Added properties for bank account events stream consumption
- `build.gradle.kts` - Added dependency on `bank-account-contract-proto`

### 5. Configuration (`bank-transaction-boot-app`)

**Updated:**
- `application.yml` - Added configuration for:
  - `bank-account-events-stream`: Stream name from bank-account service
  - `bank-account-events-consumer-group`: Consumer group name (bank-transaction-service)
  - `bank-account-events-consumer-name`: Consumer name (account-view-updater)

## Architecture Flow

```
Bank Account Service
    ↓ (publishes to Redis Stream)
Redis Stream: bank-account.event.bank-accounts
    ↓ (consumed by)
InboundBankAccountRedisStreamConfiguration
    ↓ (maps protobuf to application event)
BankAccountEventMapper
    ↓ (sends to channel)
inboundBankAccountEventsChannel
    ↓ (processed by)
AccountCreatedEventHandler
    ↓ (saves to)
AccountViewRepository
    ↓ (persisted in)
Redis (AccountView hash)
```

## Key Design Decisions

1. **No Event Propagation**: The AccountView is a simple read model that doesn't emit any events when created or updated, as it's purely for local queries within the bank-transaction bounded context.

2. **Account Subpackage**: AccountView is organized in the `domain.account` subpackage to clearly separate concerns within the domain.

3. **Minimal Data**: The view stores only the account ID, keeping it lightweight and focused on the minimal information needed for transaction validation.

4. **Clean Architecture**: Follows the hexagonal architecture pattern with clear separation of layers:
   - Domain: Pure domain model
   - Application: Use cases and ports
   - Adapters: Infrastructure implementations

5. **Async Processing**: Uses Spring Integration channels with Redis-backed message store for reliable asynchronous processing.

## Build Status

✅ All modules compile successfully
✅ Spotless formatting applied
✅ Build successful: `./gradlew :bank-transaction:build`

## Configuration

```yaml
adapters:
  redis:
    stream:
      bank-account-events-stream: bank-account.event.bank-accounts
      bank-account-events-consumer-group: bank-transaction-service
      bank-account-events-consumer-name: account-view-updater
```

## Testing

To test the integration:
1. Start both services (bank-account and bank-transaction)
2. Create an account via bank-account service
3. The account view will be automatically created in bank-transaction service
4. Query the AccountView via the repository to verify it exists

