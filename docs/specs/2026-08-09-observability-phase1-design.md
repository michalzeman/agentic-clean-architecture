---
name: "Observability Phase 1"
status: in-progress
style: compact
created: 2026-08-09
updated: 2026-08-15
related_plans: ["docs/superpowers/plans/2026-08-11-observability-step1-connector-module.md"]
---
<!-- style: compact (default) — BLUF, active voice, <=20 words/sentence, verbs not nouns, no filler. terse: also <=15 words/sentence, prefer bullets for lists and steps; single sentences for standalone points, standard technical abbreviations only (DB/auth/config), zero hedging. verbose: full engineering prose, hedging/nominalization allowed. All styles: grammatically correct English. See to-spec skill for full rules. -->

# Observability Phase 1 — Metrics Pipeline for the Two Bank Services

## How to Resume

> Instructions for any agent resuming work from this spec without the `to-spec` skill loaded.

1. Check `status` in YAML frontmatter for current state
2. Scan `### Implementation Order` for unchecked steps — these define what to do next
3. Read `## Open Blockers` for current blockers
4. Set `status: in-progress` in YAML frontmatter when starting work
5. When finished, run the **Session End Protocol** below

> Session history is audit-only — consult entry files via `observability-phase1-logs/index.md` on demand (e.g., tracing why something changed); it is not part of the resume path.

## Problem

Both services run WebFlux with suspend controllers. `bank-account-service` (8080) and `bank-transaction-service` (8081, hosts the saga) expose only `/actuator/health` and `/actuator/info`. They run on Spring Boot 4.0.0, Kotlin 2.2.0, Java 21, with a shared-kernel connector pattern (`connector-redis-starter`, `connector-postgresql-starter`, `connector-nats-jetstream-starter`, `connector-health`). No metrics registry, no Prometheus scrape, no Grafana, no dashboards exist. System-test failures currently surface only as container logs. Phase 1 ships a metrics pipeline so JVM/HTTP/channel metrics render in Grafana and custom business counters capture transfer outcomes.

## Goals

- [ ] Both services expose `/actuator/prometheus` scraped by a Prometheus container running via the existing `compose.yaml`.
- [ ] Grafana renders a community JVM dashboard and a custom bank-domain dashboard backed by Prometheus.
- [ ] Redis, PostgreSQL, and NATS infrastructure metrics are also scraped and visible in Grafana.
- [ ] A new `shared-kernel/connector-observability` module centralizes the micrometer-prometheus dependency, ObservationRegistry config, and bounded business-meter definitions, matching the existing `connector-*-starter` pattern.
- [ ] Bounded custom business counters (`bank.transfer.completed`, `bank.transfer.rolled_back`, `bank.account.operation`) emit per the Q15 inventory from a `coroutine-aware helper` (deferred to Phase 2) — for Phase 1, plain `MeterRegistry` counter increments at terminal handler sites.
- [ ] `docker compose up` starts Prometheus and Grafana unconditionally (always-on, no profile gate).
- [ ] New gradle tasks `observabilityUp` and `observabilityDown` manage the monitoring stack lifecycle.

## Non-Goals

- **Tracing / cross-service span propagation** — Phase 2 scope. Tracing requires OTel collector + Tempo + manual trace-context propagation across the custom `RedisStreamConsumer` and JetStream consumer.
- **`@Observed` annotation on suspend functions** — proven broken via bytecode inspection. `ObservedAspect.observe()` takes the synchronous else branch for suspend functions; scope closes before the coroutine body runs. See Decisions Log D12.
- **Spring Integration HANDLER observation** — proven broken on suspend `@ServiceActivator` methods. `CoroutinesUtils.invokeSuspendingFunction()` returns a `Mono`; the handler observation wraps only `doHandleMessage()` synchronous dispatch and closes the scope before the Mono is subscribed. See Decisions Log D2.
- **Spanmetrics / OTel-collector to Prometheus exporter pipeline** — deferred.
- **Alerting rules** — not addressed.
- **Refactoring suspend handlers to `Mono`/`Flow`** — explicitly rejected to preserve idiomatic Kotlin style.
- **Adopting Spring Integration native Redis Stream inbound adapter** — explicitly rejected; the existing custom `RedisStreamConsumer` in `connector-redis-starter` stays.
- **High-cardinality metrics** — no account-id, no transaction-id, no aggregate-id as tag values.

## Design Principles

- **Match the shared-kernel connector pattern.** New observability code lives in `shared-kernel/connector-observability`, mirroring `connector-redis-starter` / `connector-postgresql-starter` / `connector-health`.
- **Always-on monitoring infra.** Prometheus + Grafana containers start with every `docker compose up`, not gated behind a profile. The user's stated aim is "in case system tests fail I want to use this for the debug" — monitoring must be available without an explicit opt-in.
- **Bounded cardinality only.** Every custom tag draws from a closed enum. Adding tag values requires a code change, preventing tag explosion.
- **No invasive refactoring.** Phase 1 adds dependencies, config, and bounded business-counter increments at terminal handler sites. It does not touch suspend function signatures, Spring Integration channel topology, or the custom stream consumers.
- **Spring Boot 4 BOM-managed versions.** Use `micrometer-registry-prometheus` version managed by `spring-boot-dependencies:4.0.0` (BOM imports `micrometer-bom:1.16.0`). Add no explicit micrometer version overrides.
- **Repo-local observability config.** Prometheus and Grafana provisioning live in a top-level `observability/` directory mounted into containers via compose volume binds. Infra configs are not packaged inside Java artifacts.

## Design

### System Architecture

Phase 1 adds five always-on containers to `compose.yaml`: `prometheus`, `grafana`, `redis-exporter`, `postgres-exporter`, `nats-exporter`. NATS still exposes its native JSON monitor endpoints on port 8222 via the `-m 8222` flag; the `nats-exporter` sidecar scrapes them and re-exposes Prometheus format. The two bank services are unchanged at the container level; they gain a single new shared-kernel dependency (`connector-observability`) that brings `micrometer-registry-prometheus` + management config exposing `/actuator/prometheus`. Prometheus scrapes five targets: `bank-account-service:8080`, `bank-transaction-service:8081`, `redis-exporter:9121`, `postgres-exporter:9187`, `nats-exporter:7777`. Grafana boots with two provisioned datasources (Prometheus) and two provisioned dashboards (community JVM + custom bank). A new gradle task `observabilityUp` mirrors `dockerComposeUp` for the monitoring stack only; `observabilityDown` tears it down.

### Implementation Order

- [x] **Step 1 — Create shared-kernel/connector-observability module**
  - **What:** Scaffold a new gradle module mirroring `connector-health` and `connector-redis-starter`. The `build.gradle.kts` declares `micrometer-registry-prometheus` and `spring-boot-starter-actuator`. An `ObservabilityAutoConfiguration` registers the registry and exposes `/actuator/prometheus`. Add the classpath `AutoConfiguration.imports` entry. Include a `management.endpoints.web.exposure.include: health,info,prometheus` fragment and `spring.integration.management.observation-patterns: "*"` under a Spring profile `observability`.
  - **Touches:** New `shared-kernel/connector-observability/` directory; root `settings.gradle.kts` (include the module).
  - **Depends on:** —
  - **Coupling/gate:** Must not change existing service container ports or actuator contract (still exposes `health,info` for existing Dockerfile healthchecks); only adds `prometheus` to the include list.
  - **Done when:** `./gradlew :shared-kernel:connector-observability:build` passes; jar contains the auto-config imports file; unit test asserts `PrometheusMeterRegistry` bean is registered under the `observability` profile.

- [x] **Step 2 — Wire both boot-apps to depend on connector-observability**
  - **What:** Fix the profile-activation gap. `compose.yaml` lines 77 and 111 set `SPRING_PROFILES_ACTIVE=${SPRING_PROFILE_GROUP:-postgres-nats}` — env replaces `spring.profiles.active`, it does not merge; adding `observability` to `spring.profiles.active` alone is dropped at container runtime. Fix: add `observability` to every existing profile group (`redis-all`, `postgres-nats`, `hybrid`, `postgres-only`) in each `application.yml` AND to the base `spring.profiles.active` list. Spring expands group membership regardless of how the group name arrives. Do not edit `compose.yaml` (Step 4 owns it). Add `implementation(project(":shared-kernel:connector-observability"))` to both boot-app `build.gradle.kts` files, alongside the existing `connector-health` line. One line each. Add one `@SpringBootTest` + `@Testcontainers` integration test class per boot-app (`BankAccountObservabilityIntegrationTest.kt`, `BankTransactionObservabilityIntegrationTest.kt`) that boots the full app context with inline Testcontainers containers (`redis:7-alpine` on port 6379, `postgres:16-alpine` with user `bank_user` / password `bank_pass` / database `bank_db`, `nats:2.10.7-alpine` with `--jetstream` on port 4222). `@DynamicPropertySource` overrides Redis url, Postgres datasource url, and NATS url with container mapped ports. Each test asserts three things: (1) `GET /actuator/prometheus` returns 200 and the `/actuator/prometheus` scrape body contains `jvm_memory_used_bytes`; (2) a `WebTestClient` GET to `/actuator/health` generates HTTP traffic that Spring Boot metrics record, then a second `WebTestClient` GET to `/actuator/prometheus` confirms the `/actuator/prometheus` scrape body contains `http_server_requests_seconds`; (3) injected `Environment.getActiveProfiles()` includes `observability`. Add `testImplementation` for `org.testcontainers:testcontainers`, `org.testcontainers:postgresql`, `org.testcontainers:junit-jupiter` to each boot-app `build.gradle.kts`. Redis and NATS use `GenericContainer` (no first-class Testcontainers modules); existing `spring-boot-starter-test` covers `WebTestClient` / JUnit 5 / AssertJ.
  - **Touches:** `bank-account/bank-account-boot-app/build.gradle.kts`, `bank-transaction/bank-transaction-boot-app/build.gradle.kts`, both `src/main/resources/application.yml` files, new test files at `bank-account/bank-account-boot-app/src/test/kotlin/mz/bank/account/observability/BankAccountObservabilityIntegrationTest.kt` and `bank-transaction/bank-transaction-boot-app/src/test/kotlin/mz/bank/transaction/observability/BankTransactionObservabilityIntegrationTest.kt`.
  - **Depends on:** Step 1.
  - **Coupling/gate:** The `observability` profile is additive — existing profile groups keep their original members and gain `observability`. Dockerfile healthchecks (`/actuator/health/readiness`) remain the actuator-verified path.
  - **Done when:** Both boot-apps build (`./gradlew bootJar`); `./gradlew :bank-account:bank-account-boot-app:test` and `:bank-transaction:bank-transaction-boot-app:test` pass and each test class asserts `/actuator/prometheus` returns 200 and the `/actuator/prometheus` scrape body contains JVM metrics (`jvm_memory_used_bytes`) AND HTTP metrics (`http_server_requests_seconds`) after the test exercises `/actuator/health` to generate traffic, and the `observability` profile is active; Testcontainers containers start and stop cleanly with no port collisions on the host.

- [x] **Step 3 — Add observability/ top-level directory and Prometheus config**
  - **What:** Create `observability/prometheus/prometheus.yml` with five scrape jobs: `bank-account-service` (`bank-account-service:8080`, `/actuator/prometheus`, 15s), `bank-transaction-service` (`bank-transaction-service:8081`, same), `redis-exporter` (`redis-exporter:9121`), `postgres-exporter` (`postgres-exporter:9187`), `nats` (`nats-exporter:7777`). Create `observability/grafana/provisioning/datasources/datasource.yml` provisioning a Prometheus datasource pointing at `http://prometheus:9090`. Create `observability/grafana/provisioning/dashboards/dashboards.yml` pointing at `/var/lib/grafana/dashboards`. Drop the community JVM dashboard JSON into `observability/grafana/dashboards/jvm.json` (use the micrometer-contrib JVM dashboard or equivalent, version pinned). Add a placeholder `observability/grafana/dashboards/bank-custom.json` — its panels are filled in Step 8.
  - **Touches:** New `observability/` directory tree (no existing files modified).
  - **Depends on:** —
  - **Coupling/gate:** No external consumers — infra config only.
  - **Done when:** All listed files exist; `prometheus.yml` contains exactly five scrape_job entries with the listed targets; `datasource.yml` references `http://prometheus:9090`.

- [x] **Step 4 — Extend compose.yaml with monitoring containers and NATS monitor**
  - **What:** Add five always-on services (no profile gate) to `compose.yaml`:

    - `prometheus` (image `prom/prometheus:v3.5.0`, port `9090:9090`, binds `./observability/prometheus/prometheus.yml:/etc/prometheus/prometheus.yml:ro`)
    - `grafana` (image `grafana/grafana:12.0.0`, port `3000:3000`, env `GF_SECURITY_ADMIN_PASSWORD=admin` + `GF_USERS_ALLOW_SIGN_UP=false`, volumes for provisioning + dashboards)
    - `redis-exporter` (image `oliver006/redis_exporter:v1.5.2`, port `9121:9121`, env `REDIS_ADDR=redis-db:6379`, depends on `redis-db`)
    - `postgres-exporter` (image `prometheuscommunity/postgres-exporter:v0.17.1`, port `9187:9187`, env `DATA_SOURCE_NAME=postgresql://bank_user:bank_pass@postgres-db:5432/bank_db?sslmode=disable`, depends on `postgres-db`)
    - `nats-exporter` (image `natsio/prometheus-nats-exporter:0.14.0`, port `7777:7777`, command `["-varz","-jsz","","-connz","-routez","-subz","http://nats-db:8222"]`, depends on `nats-db`; scrapes NATS monitor JSON and re-exposes Prometheus format — replaces the abandoned D6 assumption of native `/metrics` at 8222, see D15)

    Modify `nats-db` to add `-m 8222` to its command and document the monitor port `8222`. Do not gate any of these behind the `system-tests` profile.
  - **Touches:** `compose.yaml`.
  - **Depends on:** Step 3 (Prometheus config must exist before container can mount it).
  - **Coupling/gate:** Existing `redis-db`, `redisinsight`, `postgres-db`, `nats-db`, and the two profile-gated bank services are unchanged in ports and env. New services live alongside the existing infrastructure.
  - **Done when:** `docker compose up -d` (no `--profile` flag) starts `redis-db`, `redisinsight`, `postgres-db`, `nats-db`, `nats-exporter`, `prometheus`, `grafana`, `redis-exporter`, `postgres-exporter`; `curl http://localhost:9090/-/healthy` returns 200; `curl http://localhost:3000/api/health` returns 200; `curl http://localhost:9121/metrics` returns Redis metrics; `curl http://localhost:9187/metrics` returns Postgres metrics; `curl http://localhost:7777/metrics` returns NATS metrics (via `nats-exporter` sidecar; see D15).

- [x] **Step 5 — Add observabilityUp / observabilityDown gradle tasks**
  - **What:** Register two `Exec` tasks in root `build.gradle.kts`. They mirror `dockerComposeUp` / `tearDownDockerCompose` but target only the five monitoring containers.

    `observabilityUp` runs `docker compose up -d --wait prometheus grafana redis-exporter postgres-exporter nats-exporter`. NATS monitor port 8222 is available via Step 4's `nats-db` change; `nats-exporter` scrapes it on port 7777.

    `observabilityDown` runs `docker compose rm -sf prometheus grafana redis-exporter postgres-exporter nats-exporter`. Omit `bootJar` dependencies in `observabilityUp` — it is infra-only.
  - **Touches:** Root `build.gradle.kts`.
  - **Depends on:** Step 4.
  - **Coupling/gate:** No interaction with `dockerComposeUp` (which uses `--profile system-tests`). The two task groups remain independent.
  - **Done when:** `./gradlew observabilityUp` brings up the five monitoring containers; `./gradlew observabilityDown` tears them down; `./gradlew dockerComposeUp` still works as before.

- [x] **Step 6 — Add bounded rollback-reason enum in bank-transaction-domain**
  - **What:** In `bank-transaction/bank-transaction-domain/src/main/kotlin/.../domain/`, add an enum `TransactionRollbackReason` with values `TIMEOUT`, `DEPOSIT_FAILED`, `WITHDRAWAL_FAILED`, `MANUAL_CANCEL`, `UNKNOWN`. Wire `BankTransactionAggregate.cancelBankTransaction` (or the equivalent decision site) to expose the chosen reason — the loci of the state transition is the aggregate, not the handler. Tag the `bank.transfer.rolled_back` meter with the enum's lowercase name. Add a defaulted `reason` field (default `UNKNOWN`) to `CancelBankTransaction` and `BankTransactionRolledBack`; add a nullable `rollbackReason` state field to `BankTransaction`; the reducer sets it from the event.
  - **Touches:** `bank-transaction/bank-transaction-domain` (new enum + aggregate change). Any existing test for `BankTransactionAggregate` must still pass; add a unit test asserting the reason selection.
  - **Depends on:** Step 1 (so the `MeterRegistry` bean exists for the application-layer counter calls in Step 7).
  - **Coupling/gate:** Enum is additive; no existing API breaks. The aggregate method signatures remain pure synchronous functions.
  - **Done when:** `./gradlew :bank-transaction:bank-transaction-domain:test` passes; new test asserts reason selection across the cancellation paths.

- [ ] **Step 7 — Instrument terminal business counters in both application layers**
  - **What:** In `bank-transaction/bank-transaction-application` (terminal sites: `handleFinish` for `bank.transfer.completed{outcome=ok|fail}`, `handleCancel` for `bank.transfer.rolled_back{reason=<enum>}`) and `bank-account/bank-account-application` (terminal sites: each `handleX` in `BankAccountCommandHandler` for `bank.account.operation{op=<create|deposit|withdraw|transfer-withdraw|transfer-deposit|rollback-withdraw|rollback-deposit|finish>, outcome=ok|fail}`), inject `MeterRegistry` and call `counter.increment()` at the terminal event-emission point. No tracing, no `@Observed`, no suspend-function wrapping — Phase 1 emits counter increments only, strictly at the terminal path where outcome is known. Two bounded enums live in the domain layer. Step 6 added `TransactionRollbackReason` (`bank-transaction-domain`). This step adds `BankAccountOperation` (`bank-account-domain`) for the `op` tag values. Handlers read the enum, map to lowercase, call the counter.
  - **Touches:** `bank-transaction/bank-transaction-application` (2 handler sites + MeterRegistry constructor injection + bounded-meter helper), `bank-transaction/bank-transaction-domain` (already touched by Step 6), `bank-account/bank-account-application` (multiple handler sites + MeterRegistry injection), `bank-account/bank-account-domain` (new `BankAccountOperation` enum).
  - **Depends on:** Steps 2 (services wired) and 6 (rejection-reason enum exists).
  - **Coupling/gate:** MeterRegistry is injected as a constructor dependency; if no MeterRegistry bean exists (e.g. observability profile inactive in a non-boot context), tests must use a `SimpleMeterRegistry` or mock. Counter increments must never throw — wrap in try/catch or use noop-safe patterns.
  - **Done when:** `./gradlew build` passes; both domain + both application test suites pass; smoke-test running both services shows non-zero counters after triggering one saga via existing REST controllers; Prometheus scrapes the counters and `bank_transfer_completed_total{outcome="ok"}` appears in the Prometheus query UI.

- [ ] **Step 8 — Build the custom bank-domain Grafana dashboard**
  - **What:** Fill in `observability/grafana/dashboards/bank-custom.json` with panels for: (1) `bank_transfer_completed_total` by outcome (stat + timeseries), (2) `bank_transfer_rolled_back_total` by reason (stat + pie), (3) `bank_account_operation_total` by op + outcome (timeseries stacked), (4) `http_server_requests_seconds` p95 by service (timeseries), (5) `spring_integration_channel_send_duration_seconds` p95 by channel name (timeseries; PRODUCER observation only — HANDLER observation is suppressed, see D2), (6) `spring_integration_channel_send_total` by channel (timeseries). Save the JSON via Grafana export format (dashboard JSON with `datasource: Prometheus`).
  - **Touches:** `observability/grafana/dashboards/bank-custom.json`.
  - **Depends on:** Steps 4 + 7 (Prometheus is scraping the services; counters exist).
  - **Coupling/gate:** Dashboard JSON references metric names that must exist in Prometheus scrape output. If Step 7 ships first, the dashboard renders real values; rendering empty panels before Step 7 is acceptable for verification of the dashboard scaffolding only.
  - **Done when:** After `./gradlew observabilityUp` + `docker compose --profile system-tests up -d` + triggering one saga via POST to `BankTransactionController.createBankTransaction`, Grafana at http://localhost:3000 shows the bank-custom dashboard with non-zero values in the counters panels and the JVM dashboard renders JVM metrics.

- [ ] **Step 9 — Phase 1 acceptance verification**
  - **What:** End-to-end check. Run `docker compose down -v`, then `./gradlew build`, then `docker compose --profile system-tests up -d --wait --build`, then trigger one full saga via the REST API (`POST /api/v1/transactions` then the validate-withdraw / validate-deposit / finish endpoints). Verify: Prometheus scrapes all five targets (status "UP" in the Prometheus UI targets page); `/actuator/prometheus` on both services returns JVM + HTTP + custom counter content; Grafana loads both provisioned dashboards; custom counter panels show non-zero values for at least one outcome. Run `./gradlew test` to confirm no existing test regressed.
  - **Touches:** Running infrastructure + both services + Grafana UI inspection. No file changes.
  - **Depends on:** Steps 1–8.
  - **Coupling/gate:** Phase 1 is considered complete when this step passes. Phase 2 (tracing, OTel collector, Tempo, manual trace propagation, log/trace MDC, trace-assertion system tests) is a separate spec, started only after Phase 1 is green.
  - **Done when:** All criteria above pass; documented in the next Session End Protocol entry.

### Component: shared-kernel/connector-observability

#### Internal Architecture

A Spring Boot auto-configuring starter module. Single `ObservabilityAutoConfiguration` class registers `PrometheusMeterRegistry` (already auto-created by `PrometheusMetricsExportAutoConfiguration`; this module ensures the actuator endpoint exposure) and applies the management config via a classpath `application-observability.yml`. Activated by Spring profile `observability`. Follows the same pattern as `connector-redis-starter`: a Spring Boot auto-configuration class + an `AutoConfiguration.imports` file.

#### Data Model

No persistent data. Runtime beans: `PrometheusMeterRegistry`, `ObservationRegistry` (auto-provided by `ObservationAutoConfiguration`), the `PrometheusActuatorEndpoint`. No domain types.

#### API / Interface

Public API is the actuator endpoint `/actuator/prometheus` exposed by both boot-apps once they depend on the module and activate the `observability` profile. No Java API surface for clients. The module's `application-observability.yml` declares:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health, info, prometheus
  endpoint:
    health:
      show-details: always
  prometheus:
    metrics:
      export:
        enabled: true
spring:
  integration:
    management:
      observation-patterns: "*"
```

#### Error Handling

Auto-configuration is `@ConditionalOnClass(MeterRegistry.class)` and `@ConditionalOnBean(PrometheusMeterRegistry.class)`; if micrometer-prometheus is absent the config silently no-ops. No throwing paths; startup failures surface via standard Spring Boot failure analyzer.

### Component: per-service observability activation

#### Internal Architecture

Both boot-apps activate the `observability` Spring profile by default (alongside their existing `redis-all`/`postgres-nats` profile groups). The profile is additive — it overlays management config on top of the service-specific adapter config. Each service has its own `MeterRegistry` (auto-provided by Spring Boot). No shared registry across services — each is scraped individually by Prometheus.

#### Data Model

No persistent data.

#### API / Interface

`GET /actuator/prometheus` on `bank-account-service:8080` and `bank-transaction-service:8081` returns Prometheus exposition-format text. Existing endpoints (`/actuator/health`, `/actuator/info`, `/actuator/health/readiness`) are unchanged.

#### Error Handling

If the `observability` profile is inactive (e.g. unit tests not booting the full context), no `/actuator/prometheus` endpoint is registered and no MeterRegistry bean is present. Application-layer counter injection (Step 7) must degrade gracefully when MeterRegistry is missing — tests inject `SimpleMeterRegistry` or mock the dependency.

### Component: custom business meters

#### Internal Architecture

Three custom meters, all Counters (no Timers in Phase 1 — saga-stage timers require the coroutine-aware observation wrapper from Phase 2):

- `bank.transfer.completed` tags: `outcome` in {`ok`, `fail`}
- `bank.transfer.rolled_back` tags: `reason` in bounded enum from Step 6 (`TransactionRollbackReason` lowercase names)
- `bank.account.operation` tags: `op` in bounded enum from Step 7 (`BankAccountOperation` lowercase names), `outcome` in {`ok`, `fail`}

Counters increment exactly once per terminal signal in the application-layer handlers. No partial increments. Instrumentation lives in the application layer, not the domain layer (the domain is pure synchronous functions; the application layer coordinates I/O and outcomes).

#### Data Model

Two bounded enums live in domain layers (loci of state transitions):

- `bank-transaction-domain`: `TransactionRollbackReason { TIMEOUT, DEPOSIT_FAILED, WITHDRAWAL_FAILED, MANUAL_CANCEL, UNKNOWN }`
- `bank-account-domain`: `BankAccountOperation { CREATE, DEPOSIT, WITHDRAW, TRANSFER_WITHDRAW, TRANSFER_DEPOSIT, ROLLBACK_WITHDRAW, ROLLBACK_DEPOSIT, FINISH }`

Outcome tag is a literal two-value enum, not a domain type — defined locally in the application layer or in `connector-observability` (`OperationOutcome { OK, FAIL }`).

#### API / Interface

A small meter-increment helper in `connector-observability` (e.g. an `ObservabilityMeters` bean exposing `transferCompleted(outcome: OperationOutcome)`, `transferRolledBack(reason: TransactionRollbackReason)`, `accountOperation(op: BankAccountOperation, outcome: OperationOutcome)`). Handlers inject `ObservabilityMeters` (or directly `MeterRegistry` plus the helper) and call the relevant method at the terminal signal.

#### Error Handling

Counter increments must never propagate exceptions — even if the meter registry throws, the saga must complete its domain operation. The `ObservabilityMeters` helper wraps increments in a try/catch that logs at WARN and continues. This is defensive because Phase 2 will replace these manual increments with the coroutine-aware `withObservation` wrapper, which has different failure semantics.

### Component: compose extensions

#### Internal Architecture

Five always-on containers in `compose.yaml`: `prometheus` (9090), `grafana` (3000), `redis-exporter` (9121), `postgres-exporter` (9187), `nats-exporter` (7777). NATS `nats-db` gains the `-m 8222` flag for its native JSON monitor port; `nats-exporter` scrapes that JSON and re-exposes it as Prometheus exposition format on `7777/metrics`. No networks — default bridge as with the existing services. Volumes bind the top-level `observability/` directory contents into the containers as read-only configs.

#### Data Model

No persistent data volumes required for Phase 1 (Grafana can use ephemeral SQLite for its own metadata; Prometheus can use ephemeral storage — dashboards are provisioned from disk on every container start, so no state is lost on tear-down).

#### API / Interface

Container ports are published to the host: Grafana `3000`, Prometheus `9090`, Redis exporter `9121`, Postgres exporter `9187`, NATS exporter `7777`. Internal compose service names (`prometheus`, `grafana`, etc.) are used in Prometheus scrape configs and Grafana datasource URLs.

#### Error Handling

Container startup failures surface via `docker compose up --wait` non-zero exit. Grafana provisioning failures (bad dashboard JSON) surface as grafana container log errors and are visible via `docker compose logs grafana`.

### Cross-Component Interactions

Prometheus queries Grafana at `http://prometheus:9090`. Bank services respond to Prometheus pulls at `bank-account-service:8080/actuator/prometheus` and `bank-transaction-service:8081/actuator/prometheus` every 15s. Both services must be running for the scrape to succeed; if only the infra containers are up (no `--profile system-tests`), the bank-service scrape targets show status "DOWN" in Prometheus UI — this is expected and acceptable.

Redis exporter connects to `redis-db:6379` using the existing Redis instance (no password). Postgres exporter uses `DATA_SOURCE_NAME=postgresql://bank_user:bank_pass@postgres-db:5432/bank_db?sslmode=disable` (same credentials as the existing Postgres service in compose). NATS monitor at `nats-db:8222` exposes a native JSON monitoring API (`/varz`, `/jsz`, `/connz`, `/healthz`, ...). The `nats-exporter` container scrapes these JSON endpoints and re-exposes them in Prometheus exposition format at `nats-exporter:7777/metrics`; Prometheus scrapes that. The earlier assumption (D6) that NATS 2.10.7 exposes `/metrics` natively was proven false by end-to-end verification (404 at `/metrics`). See D15.

Gradle task `observabilityUp` lives alongside `dockerComposeUp`; the two are independent. `observabilityUp` starts monitoring infra only; `dockerComposeUp` starts the system-tests-profile composition (infra + both services). To run everything at once the user invokes both: `./gradlew observabilityUp && ./gradlew dockerComposeUp`, or directly `docker compose --profile system-tests up -d` (which starts everything since the monitoring containers are not profile-gated).

## Decisions Log

| Decision | Rationale | Date |
|----------|-----------|------|
| D1 — Metrics-only in Phase 1; tracing deferred to Phase 2 spec | Tracing requires invasive manual trace-context propagation across the custom `RedisStreamConsumer` and NATS JetStream consumer; would muddy Phase 1's validation surface. Ship metrics pipeline first, de-risk tracing later. | 2026-08-09 |
| D2 — Suppress Spring Integration HANDLER observation; enable PRODUCER observation | Bytecode-verified: handler observation wraps only synchronous `doHandleMessage()` dispatch on suspend `@ServiceActivator`s, closing scope before the `Mono` (returned by `CoroutinesUtils.invokeSuspendingFunction`) is subscribed. PRODUCER observation (channel `send()`) is synchronous and works correctly. | 2026-08-09 |
| D3 — New `shared-kernel/connector-observability` module | Matches the established `connector-*-starter` pattern; centralizes micrometer-prometheus dependency and management config in one place; any future Phase-2 tracing additions land in the same module. | 2026-08-09 |
| D4 — Prometheus + Grafana always-on in compose (no profile gate) | User stated aim is "in case system tests fail I want to use this for debug" — monitoring must be available without explicit opt-in. Infra-only scans still get monitoring; profile-gated services show as DOWN but monitoring infra is up. | 2026-08-09 |
| D5 — Community JVM + custom bank-domain dashboards | Community JVM dashboard gives instant value with zero custom-metric work. Custom bank dashboard surfaces the bounded business counters defined in Step 7. | 2026-08-09 |
| D6 — Scrape infrastructure exporters too: Redis + Postgres + NATS native monitor | System-test failures involve deadlocks, connection exhaustion, and stream consumer lag — directly observable via infra exporters. Small container cost (~20MB each). NATS native monitor is a single CLI flag, no extra container. **Reversed by D15 (2026-08-15) — NATS 2.10.7 lacks `/metrics`; nats-exporter sidecar added.** | 2026-08-09 |
| D7 — Custom metrics inventory accepted as specified | Three Counters (`bank.transfer.completed`, `bank.transfer.rolled_back`, `bank.account.operation`); bounded tag enums; explicit suppression of broken SI handler observation; PRODUCER channel metrics from auto-observation; no saga-stage Timers in Phase 1. | 2026-08-09 |
| D8 — Bounded rollback-reason enum lives in `bank-transaction-domain` | The aggregate's `cancelBankTransaction` is the loci of the state transition that determines the reason. Domain owns the enum; application handler reads it and tags the meter. `connector-observability` stays domain-agnostic. | 2026-08-09 |
| D9 — Phasing: ship metrics-only first, then tracing | Two phases, two specs, two review cycles. Phase 1 validates the pipeline (Prometheus scrapes, Grafana renders, counters emit). Phase 2 adds OTel collector + Tempo + manual saga-stage observation + trace propagation across Redis Streams / NATS JetStream. | 2026-08-09 |
| D10 — Prometheus scrapes directly; OTel collector handles only traces (Phase 2) | Canonical Spring Boot pull-model pipeline. Spanmetrics / OTel-collector-as-metrics-pipeline deferred. | 2026-08-09 |
| D11 — Repo-local `observability/` top-level directory | Observability infra configs are deployment configs, not Java artifacts. Standard community layout. Mounted into containers via compose volume binds. | 2026-08-09 |
| D12 — No `@Observed` annotation on suspend functions | Bytecode-verified: `ObservedAspect.observe()` has only two branches — `CompletionStage` and synchronous else. Suspend functions compile to methods returning `Object.class` (not `CompletionStage`) with a `Continuation` parameter. The synchronous branch fires; observation scope closes immediately. Micrometer 1.16.0 has zero coroutine awareness. No `CoroutineObservedAspect` exists. | 2026-08-09 |
| D13 — Pin JVM dashboard to Grafana dashboard 4701, revision 10 (Apache-2.0) | The "JVM (Micrometer)" dashboard by mweirauch is the de-facto Spring Boot + Micrometer pairing (~80KB, 2973 lines, covers `jvm_memory_*`, `jvm_threads_*`, `jvm_gc_*`, `process_*`). Pinned to revision 10 (2023-05-04) via `https://grafana.com/api/dashboards/4701/revisions/10/download`. Apache-2.0 license. | 2026-08-15 |
| D14 — bank-custom.json ships as panel skeleton (6 titled panels, blank PromQL) | Step 3 scaffold verifies Grafana provisioning loads the dashboard before Step 7 counters exist. Six panels with titles and Prometheus datasource set; PromQL filled in Step 8. Alternative (empty stub) gives no provision-load verification; alternative (full PromQL) duplicates Step 8 work. | 2026-08-15 |
| D15 — Reverse D6: add `nats-exporter` sidecar; NATS 2.10.7 has no native `/metrics` | End-to-end Step 4 verification showed `curl http://localhost:8222/metrics` returns 404. NATS 2.10.7 monitor port 8222 serves JSON endpoints (`/varz`, `/jsz`, `/connz`, ...) only — no Prometheus exposition format. Reversed D6's "single CLI flag, no extra container" claim. Added `natsio/prometheus-nats-exporter:0.14.0` container on port 7777 that scrapes NATS monitor JSON and re-exposes Prometheus format. Five monitoring containers now, not four. | 2026-08-15 |

## Cross-Repository References

> Phase 1 works entirely within this single repository (`agentic-clean-architecture`). No external specs, contracts, or cross-repo dependencies. Phase 2 (separate spec) will reference the OTel collector and Tempo upstream docs and may need a downstream event-propagation contract between `connector-redis-starter` and `connector-nats-jetstream-starter`.

| Repository | Reference | Notes |
|------------|-----------|-------|
| — | — | All Phase 1 work is contained within this repository. |

## Open Blockers

None

## Session End Protocol

> **MANDATORY:** Before ending any session that works from this spec, run the handoff write.

- [ ] Reconcile `### Implementation Order` steps against impl repo: `git diff` since `updated` date, and probe each unchecked step's `Done when` criterion (flag state, service deployed, schema registered, etc.)
- [ ] Update `## Open Blockers` in place (add new, remove resolved, write the literal `None` if clear)
- [ ] Create entry file `observability-phase1-logs/YYYY-MM-DD-<slug>.md` (same-day second session: `YYYY-MM-DD-<slug>-2.md`). Create `observability-phase1-logs/` and `index.md` on first use. (Format below.)
- [ ] Append one line to `observability-phase1-logs/index.md` at the bottom, chronological; never edit or reorder existing lines. (Format below.)
- [ ] If a decision changed or was reversed this session: update the Decisions Log row and the Design section in place; record the change + rationale in the entry file.
- [ ] Bump `updated` date in frontmatter; update `status` if state changed.
- [ ] On transition to `done`: move the spec file and its `observability-phase1-logs/` directory together into `docs/specs/archive/`.

> If no impl repo exists yet (design phase), skip `git diff` and reconcile design-phase steps only.

#### Entry file format

Optional lines omitted when empty. Entry files are immutable once created — corrections go in the next session's entry.

````markdown
# YYYY-MM-DD — <verb> <what>. Next: <x>.

Updated: <spec sections touched>
Decision: <decision> (see Decisions Log)
Blocker: <blocker>
External: <external change>

<optional free detail>
````

#### Index line format

Append at the bottom, chronological; never edit or reorder existing lines:

```
- [YYYY-MM-DD](YYYY-MM-DD-<slug>.md) — <verb> <what>. Next: <x>.
```
Same-day second session: link target is `YYYY-MM-DD-<slug>-2.md`.
