---
title: "Build, Deploy, and Runtime"
block: reverse-eng/api/internal/build-deploy-and-runtime
part: A
source_repo: electric-wealth-api-spring
source_version: 0.1.0
source_commit: 8a61cdeafb4537b16221d7aa0e9300010abd3387
extracted: 2026-10-15
---

## 25. Build, Deploy, and Runtime

### 25.1 Build

| Command | Result |
|---|---|
| `./mvnw spring-boot:run` | Run from source on port 4200 |
| `./mvnw test` | Compile and run all tests (incl. contract checks) |
| `./mvnw package` | Executable jar `target/electric-wealth-api-spring-0.1.0.jar` (spring-boot-maven-plugin); runs tests first |
| `java -jar target/electric-wealth-api-spring-0.1.0.jar` | Run the jar (start it from the repo root so the default relative `DB_PATH` resolves) |

The Maven wrapper (`.mvn/wrapper/maven-wrapper.properties`) downloads Maven on first use; dependencies come from
Maven Central or a mirror configured in `~/.m2/settings.xml`.

### 25.2 CI Pipeline

None in this repo (no `.github/workflows`, no other CI config among tracked files).

### 25.3 Deploy Target

Not defined. The repo targets local development only (single `servers` entry `http://localhost:4200`; no
Dockerfile or deployment manifests).

### 25.4 Runtime Configuration

Environment variables / `.env`: `PORT`, `DB_PATH`, `CORS_ORIGINS`; any Spring property can also be overridden by
the usual Boot mechanisms. See [02-environment-and-configuration.md](02-environment-and-configuration.md).

### 25.5 Health Checks

`GET /health` → `{"status":"ok"}` (public; does not touch the database).

### 25.6 Graceful Shutdown

No explicit configuration (`server.shutdown` not set); Spring Boot defaults apply.

### 25.7 Rollout Strategy

Not defined in the repo.

### 25.8 Local vs Prod Differences (Document Explicitly)

Only a local setup exists. Items that would have to change for any shared environment: real password hashing and
MFA, TLS, CORS origins, rate-limiting behind a proxy (`X-Forwarded-For`), request size limits, a database that is
not a local file shared with the Node API, metrics/health checks.
