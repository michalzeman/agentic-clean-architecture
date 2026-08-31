# Handoff: Intermittent `TransactionJetStreamIntegrationSystemTest` Timeout

**Date:** 2026-08-21
**Status:** Investigation in progress. A partial fix is applied (keep it). Root cause of the timeout under full-build load is NOT yet confirmed.
**Command under investigation:**
`./gradlew clean build systemTest -PspringProfileGroup=postgres-nats`

## Goal for the next session

Determine why `TransactionJetStreamIntegrationSystemTest.should complete full transaction flow via JetStream path and verify account balances()` times out (180s, assertion at `TransactionJetStreamIntegrationSystemTest.kt:178`) when run as part of a full `clean build systemTest`, but PASSES in ~46s when run in isolation.

The applied fix (`.withAutoAck(true)` + send-return guards) corrected a real latent bug but did NOT resolve the timeout. The full from-scratch build still fails: `BUILD FAILED in 6m 1s`, test duration 3m31s, same assertion.

## Environment blocker (resolved, must keep the workaround)

macOS Temurin 21 hits a `posix_spawn` bug when forking external processes (`protoc`, test JVM). It aborts the build before system tests run. Workaround that works on this machine: **`--no-daemon`**. The documented `-Djdk.lang.Process.launchMechanism=VFORK` flag is unsupported on this ARM/Temurin combo. Use `--no-daemon` on every gradle invocation in this session.

## What was already done

1. Ran `./gradlew clean build systemTest -PspringProfileGroup=postgres-nats`. First two attempts died on `posix_spawn`. With `--no-daemon`, reached system tests.
2. Identified the single failing system test (see Goal).
3. Reproduced once in isolation with `-x tearDownDockerCompose` to keep containers/logs. That isolated run PASSED in 32s — the failure is load/timing-dependent.
4. Inspected NATS monitoring API (`http://localhost:8222/jsz`): both streams exist, both service consumers ack correctly, both test consumers had `ack_floor=0` (never acked).
5. Applied a fix to `BankSystemTestConfiguration.kt`: added `.withAutoAck(true)` and `send()` return-value guards to the two test consumer beans, matching the service consumers. spotless clean.
6. Verified in isolation: PASSED twice (~46s).
7. Verified from-scratch full build: **STILL FAILS** (3m31s timeout). The fix was insufficient.
8. The failed run's containers were torn down by `tearDownDockerCompose`, so logs from the failing run are GONE.

## Applied change (DO NOT revert — it is a real latent-bug fix)

File: `bank-system-tests/src/test/kotlin/mz/bank/system/tests/wiring/BankSystemTestConfiguration.kt`
- Beans `bankAccountEventsNatsConsumer` (~line 279) and `bankTransactionEventsNatsConsumer` (~line 305): added `.withAutoAck(true)` to each `JetStreamConsumerBuilder` chain.
- Both `withMessageHandler` lambdas now capture `channel.send()` return value and `throw IllegalStateException(...)` on rejection, matching the service-side consumers.

This fix is not yet committed (separate approval required). Keep it in place; it is correct regardless of the timeout root cause.

## Hypotheses ruled out (with evidence)

- **Stale durable-consumer position across runs.** `compose.yaml` mounts no named volume for `nats-db`; `tearDownDockerCompose` runs `down -v`, wiping JetStream state. No persistence across runs.
- **Service config asymmetry.** Both `application-nats-jetstream.yml` files are structurally symmetric (same streams, subjects, retention=LIMITS, deliver-policy=ALL, ack-policy=EXPLICIT, durable=true).
- **Message-stealing between test and service consumers.** JetStream durable consumers are independent; each gets its own delivery copy. No queue-group competition.
- **`autoAck=false` alone.** The fix to auto-ack did not resolve the timeout. `ack_floor=0` was a real bug, but not THE cause.

## Open hypotheses (investigate next)

- **Saga stalls under load.** Under full-build CPU/IO contention, the bank-transaction saga (`withdraw → deposit → finish`) may not complete, so `BankTransactionFinished` is never PUBLISHED. Check: does `BankTransactionFinished` appear in `bank-transaction-service` logs for the failing transaction ID?
- **Published-but-not-delivered to the test consumer under load.** The test consumer is a pull consumer. Under load, pull-batch timing or `maxAckPending`/`max-waiting-pulls` limits may delay the `TransactionFinished` fetch past 180s.
- **Startup/subscription race.** Pull consumer with `DeliverPolicy.All` should replay from stream start, but if the consumer subscribes after messages age out (max-age=7d, unlikely) or after container recreate timing, behavior could differ under load.
- **Container recreate timing.** `dockerComposeUp` (`up -d --wait --build`) may recreate service containers; under load the services may not be fully ready when the test starts emitting/receiving.

## Next steps for the fresh agent

1. **Reproduce with logs kept.** Run:
   `./gradlew clean build systemTest -PspringProfileGroup=postgres-nats --no-daemon -x tearDownDockerCompose 2>&1 | tee /tmp/repro-fail.log`
   The `-x tearDownDockerCompose` keeps containers up so logs survive.
2. **Extract the failing transaction ID** from the assertion message (`Expected TransactionFinished event for transaction <UUID>`).
3. **Trace that UUID across both services:**
   - `docker logs bank-account-service 2>&1 | grep -iF "<UUID>"`
   - `docker logs bank-transaction-service 2>&1 | grep -iF "<UUID>"`
4. **Decide: published or not?** If `BankTransactionFinished` appears in `bank-transaction-service` logs → published-but-not-delivered (NATS/consumer path). If it does NOT appear → saga stalled (service path). This bifurcates the investigation.
5. **Inspect NATS state under failure:** `curl -s http://localhost:8222/jsz` (the `nats` CLI is NOT in the container). Check stream message counts, consumer `delivered`, `ack_floor`, `num_pending` for both test and service consumers.
6. **Check service startup logs** for the failing run: profiles active, `JetStreamStreamInitializer completed: N streams`, consumer subscription lines, any ERROR/Exception.
7. If the saga stalls, trace the saga steps (Created → WithdrawalStarted → MoneyWithdrawn → DepositStarted → Finished) to find where it stops. Correlate with `bank-account-service` command processing (`WithdrawForTransfer`, `DepositFromTransfer`, `FinishTransactions`).
8. Apply the `debugging-system-tests` skill's Phase 3 (trace data flow across the 7 layers) for the `TransactionFinished` event specifically.

## Key file paths

| Purpose | Path |
|---|---|
| Failing test | `bank-system-tests/src/test/kotlin/mz/bank/system/tests/TransactionJetStreamIntegrationSystemTest.kt` (assertion L178; wait L157-168; account-wait L120-131) |
| Applied fix | `bank-system-tests/src/test/kotlin/mz/bank/system/tests/wiring/BankSystemTestConfiguration.kt` (beans ~L279, ~L305) |
| `awaitMessages` polling | `shared-kernel/connector-system-tests/src/main/kotlin/mz/shared/connector/system/tests/QueueChannelExtensions.kt` (L79-102) |
| Consumer builder + defaults | `shared-kernel/connector-nats-jetstream/src/main/kotlin/.../JetStreamConsumerBuilder.kt` (defaults: durable=true, autoAck=false, ackWait=30s, maxDeliver=3) |
| Service consumers (style ref, already auto-ack) | `bank-{account,transaction}/bank-{...}-adapter-nats-jetstream/src/main/kotlin/.../config/InboundJetStreamConfiguration.kt` |
| Service NATS YAML (symmetric) | `bank-{account,transaction}/bank-{...}-boot-app/src/main/resources/application-nats-jetstream.yml` |
| Compose + NATS config | `compose.yaml` (nats-db L43-56, no named volume); `nats-jetstream.conf` (store_dir=/data/jetstream) |
| Gradle compose tasks | `build.gradle.kts` (dockerComposeUp L126-143, tearDownDockerCompose L145-150); `bank-system-tests/build.gradle.kts` (systemTest L29-35) |
| Debugging skill | `.opencode/skills/debugging-system-tests/SKILL.md` |

## Saved build logs (in `/tmp`, may not survive reboot)

- `/tmp/gradle-build-output3.log` — the full build run that first surfaced the failure (VFORK attempt).
- `/tmp/verify-full-build.log` — 1041 lines, the from-scratch full build that STILL FAILS after the fix. Most relevant log.
- `/tmp/single-test.log`, `/tmp/verify-test.log`, `/tmp/verify-test2.log`, `/tmp/verify-test3.log` — isolated runs that PASSED.

## Current container state

Containers were torn down by the last full build's `tearDownDockerCompose`. `docker ps` is empty. The next reproduction must bring them back up (the build does this automatically via `dockerComposeUp`).

## Suggested skills

Invoke these at the start of the next session:

- **`debugging-system-tests`** (primary; located at `.opencode/skills/debugging-system-tests/SKILL.md`). Already loaded in the prior session. Follow its Phase 1-3 for the `TransactionFinished` event-flow trace. Its common-failures table and Phase 4 (config asymmetry) are already covered — focus on Phase 3 (data-flow trace) and Phase 5 (read-model/saga state) for the failing transaction ID.
- **`systematic-debugging`** — apply the root-cause methodology; do not jump to fixes. The prior session's premature root-cause call (autoAck) cost a verification cycle.
- **`verification-before-completion`** — before claiming any fix works, run the FULL from-scratch `clean build systemTest` command (not just the isolated test). The isolated test passes; only the full build reproduces the failure.
- **`clean-code`** — if further code edits are needed.
- **`project-structure`** — to orient across the hexagonal module layout if unfamiliar.

## Lessons from this session

- The isolated test passes; only the full `clean build systemTest` reproduces the timeout. Always verify with the full command, not the isolated test.
- `tearDownDockerCompose` destroys the evidence. When debugging, run with `-x tearDownDockerCompose` and inspect logs immediately.
- `autoAck=false` with `ackWait=30s` / `maxDeliver=3` is a real latent bug in the test consumers, but fixing it did not resolve the load-dependent timeout. Do not assume a single cause.
