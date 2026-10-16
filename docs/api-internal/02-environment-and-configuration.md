---
title: "Environment and Configuration"
block: reverse-eng/api/internal/environment-and-configuration
part: A
source_repo: electric-wealth-api-spring
source_version: 0.1.0
source_commit: 8a61cdeafb4537b16221d7aa0e9300010abd3387
extracted: 2026-10-15
---

## 2. Environment and Configuration

### 2.1 Prerequisites

| Tool | Version | Why |
|---|---|---|
| JDK | 21 (`<java.version>21</java.version>` in [pom.xml](../../pom.xml)) | Compile and run. Classes compiled for 21 (class file 65) fail on an older JVM. |
| Maven | Not needed — `./mvnw` wrapper downloads it | Build |
| Node.js | 24 per [README.md](../../README.md) | Only to build the SQLite database (`electric-wealth-db`) and to run the tests (`ApiTestBase` shells out to `node ../electric-wealth-db/scripts/reset.js`) |
| Sibling repo `electric-wealth-db` | — | Owns the schema and seed data; default `DB_PATH` points into it |

### 2.2 Local Development Setup

```bash
# 1. Build the database (once, or to reset data)
cd ../electric-wealth-db && npm run db:reset

# 2. Run the API (port 4200)
cd ../electric-wealth-api-spring && ./mvnw spring-boot:run
```

- API: `http://localhost:4200`; spec: `/openapi.json`; Swagger UI: `/docs`.
- If `java -version` is not 21, set `JAVA_HOME` to a JDK 21 first (README gives the Homebrew path).
- Startup fails fast if the database file is missing: `DatabaseConfig` throws `IllegalStateException("Database not found at …  Build it first: cd ../electric-wealth-db && npm run db:reset …")` ([DatabaseConfig.java](../../src/main/java/com/electricwealth/api/config/DatabaseConfig.java)).

### 2.3 Configuration Management

Configuration comes from [application.yml](../../src/main/resources/application.yml), optionally overridden by a
`.env` file in the working directory (`spring.config.import: optional:file:.env[.properties]`). `.env` is
git-ignored; [.env.example](../../.env.example) documents it.

| Setting | Env var | Default | Bound to | Used by |
|---|---|---|---|---|
| `server.port` | `PORT` | `4200` | Spring Boot | HTTP listener; also the `servers[0].url` in the spec (`OpenApiConfig`) |
| `ew.db-path` | `DB_PATH` | `../electric-wealth-db/data/electric-wealth.db` (relative to the start directory) | `AppProperties.dbPath` | `DatabaseConfig` |
| `ew.cors-origins` | `CORS_ORIGINS` | `http://localhost:5173` (comma-separated list) | `AppProperties.corsOrigins` | `SecurityConfig.corsConfigurationSource` |
| `ew.log-requests` | — | `true` | `AppProperties.logRequests` | `TraceFilter` |
| `ew.rate-limit-per-minute` | — | `300` | `AppProperties.rateLimitPerMinute` | `RateLimitFilter` |
| `spring.datasource.hikari.maximum-pool-size` | — | `4` | `HikariDataSource` (via `@ConfigurationProperties("spring.datasource.hikari")`) | `DatabaseConfig` |
| `spring.jackson.default-property-inclusion` | — | `non_null` | Jackson | All responses: null fields omitted unless `@JsonInclude(ALWAYS)` |
| `spring.autoconfigure.exclude` | — | `UserDetailsServiceAutoConfiguration` | Boot | Stops Boot creating a default in-memory user |
| `springdoc.api-docs.path` | — | `/openapi.json` (version `openapi_3_1`) | springdoc | Spec endpoint |
| `springdoc.swagger-ui.path` | — | `/docs` | springdoc | Swagger UI |
| `springdoc.default-produces-media-type` | — | `application/json` | springdoc | Spec media types |

`AppProperties` is a `@ConfigurationProperties("ew")` record, registered by `@ConfigurationPropertiesScan` on
`ElectricWealthApplication`.

The JDBC URL is built in code, not configured: `jdbc:sqlite:<absolute db path>?foreign_keys=true&busy_timeout=3000`.

Test overrides (`ApiTestBase`): `ew.log-requests=false`, `ew.rate-limit-per-minute=100000`, and `ew.db-path` set
to a temp copy built by the db repo's reset script.

### 2.4 Environment-Specific Behaviors

- **No Spring profiles** are defined (`application.yml` is the only config file; no `application-*.yml`).
- **Only one server entry** is published in the spec: `http://localhost:<port>` "Local development".
- **Relative `DB_PATH`** resolves against the process working directory, so the app must be started from the repo root (comment in `application.yml`).
- **Port 4200** was chosen so it can run next to the Node API on 4100 (`.env.example`).
- No production or staging configuration exists in this repo.
