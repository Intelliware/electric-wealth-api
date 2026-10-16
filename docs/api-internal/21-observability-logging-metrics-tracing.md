---
title: "Observability: Logging, Metrics, and Tracing"
block: reverse-eng/api/internal/observability-logging-metrics-tracing
part: A
source_repo: electric-wealth-api-spring
source_version: 0.1.0
source_commit: 8a61cdeafb4537b16221d7aa0e9300010abd3387
extracted: 2026-10-15
---

## 21. Observability: Logging, Metrics, and Tracing

### 21.1 Logging

- **Library:** SLF4J over Spring Boot's default Logback; no `logback*.xml` and no `logging.*` keys in `application.yml`, so Boot's default console format and INFO level apply.
- **Request log** — `TraceFilter`, logger name `requests`, one INFO line per request after the response:
  `GET /api/wealth/dashboard 200 12ms trace=trace-1a2b3c4d`. Toggle with `ew.log-requests` (default `true`; tests set `false`).
- **Error log** — `GlobalExceptionHandler.unexpected`: ERROR `[trace=<id>] Unhandled error` with stack trace.
- **Startup** — `DatabaseConfig` INFO `Database: <absolute path>`.
- **MDC** — `TraceFilter` puts `traceId` into the MDC for the request and removes it afterwards. The default log pattern does not print MDC values; add `%X{traceId}` to a logging pattern to use it.
- **PII:** the request log contains method, URI path (`getRequestURI()`, which excludes the query string), status, duration and trace id. No bodies, headers, tokens or user ids are logged.

### 21.2 Metrics

None. No Micrometer registry, no Spring Boot Actuator dependency in `pom.xml`.

### 21.3 Tracing

- Correlation id only: `trace-` + first 8 characters of a random UUID, generated per request (incoming trace headers are not read).
- Returned in the `X-Trace-Id` response header (exposed via CORS) and in every error body (`error.traceId`).
- `TraceIds.get(request)` falls back to a fresh id if the attribute is missing.
- No OpenTelemetry / distributed tracing.

### 21.4 Health & Readiness

- `GET /health` (`HealthController`) → `200 {"status":"ok"}`. Public, static: it does not check the database.
- No readiness/liveness split, no Actuator. The database is checked once at startup (`DatabaseConfig` fails if the file is missing).
- `/health` is subject to the rate limiter like any other path.

### 21.5 Common Pitfalls

- Expecting `traceId` in log lines: only the request-log line and the 500 log include it explicitly.
- Using `/health` as a DB health check: it returns 200 even if the database became unreadable after startup.
- Looking for 4xx details in logs: they are not logged; ask the user for the `X-Trace-Id` and the response body.
