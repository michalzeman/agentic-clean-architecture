---
status: accepted
date: 2026-09-06
---
<!-- Tracing observability stack. See also docs/specs/2026-09-06-observability-phase2-design.md. -->

# 0001: Tracing and log observability stack

Phase 1 shipped metrics only; services log with the Spring Boot default pattern, no trace exists for a saga, and no logs reach Grafana. We adopt Micrometer Tracing with the OpenTelemetry bridge, exporting spans via OTLP to Tempo. Logs become JSON on stdout, collected by Grafana Alloy into Loki. Message channels propagate trace context through an explicit `traceparent` header. This plugs into the existing `connector-observability` observation wiring, keeps one config surface, and makes one transfer followable as one trace with correlated logs across both services.

## Considered options

- **OpenTelemetry Java agent.** Rejected. The agent patches bytecode invisibly, and this stack is already fragile territory with Spring Boot 4 package moves, Kotlin coroutines, and custom message codecs. Manual instrumentation through Micrometer stays debuggable.
- **Brave as the tracing library.** Rejected. Brave has no OTLP export, so reaching Tempo needs extra translation. Micrometer Tracing's OTel bridge exports OTLP directly.
- **No tracing library.** Rejected. `traceId` and `spanId` in logs only exist when a tracer produces them. Without one, log-to-trace correlation is impossible by definition.
- **Promtail for log collection.** Rejected. Promtail is feature-frozen; Grafana moved log collection to Alloy. Alloy also autodiscovers Docker container logs and accepts OTLP, so it covers both the stdout scrape and future span handling.
- **Elasticsearch for log storage.** Rejected. It duplicates what the Grafana ecosystem already provides and adds a second storage and query stack to operate. Loki keeps logs beside traces and dashboards.

## Consequences

- All tracing and logging wiring lives in `shared-kernel/connector-observability`, matching the phase-1 actuator and Prometheus pattern.
- Sampling is 100 percent, always on. This is a demo; simplicity beats tail-sampling sophistication.
- Context propagation must be verified at every thread and process boundary. The per-hop risk register lives in the phase-2 spec, and per-hop verification is mandatory, not optional.
- Log config is centralized; test runs keep the default console pattern so test output stays readable.
