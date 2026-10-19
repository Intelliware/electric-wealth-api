# electric-wealth-api

A Spring Boot REST API for Electric Wealth, using the shared SQLite database and serving the web app.

**Status:** complete. The API includes 49 endpoints and a committed OpenAPI contract.

## Getting started

Prerequisites:

- **Java 21**: any distribution works (Temurin, Corretto, Oracle, Microsoft). Check with `java -version`.
- **Node.js 24**: only needed to build the database and to run the tests.
- Maven is **not** needed: `./mvnw` downloads it on first use. That first build also downloads the libraries from
  Maven Central, so behind a corporate firewall you need either access to it or an internal mirror (Nexus or
  Artifactory) configured in `~/.m2/settings.xml`.

Clone the database, API and web app repos side by side:

```
some-folder/
  electric-wealth-db/
  electric-wealth-api/          (this repo, port 4200)
  electric-wealth-web/
```

```bash
# 1. Build the database (once, or whenever you want fresh data)
cd ../electric-wealth-db
npm run db:reset

# 2. Run the API
cd ../electric-wealth-api
./mvnw spring-boot:run
```

The API runs at http://localhost:4200, with API docs at http://localhost:4200/docs.

If `java -version` doesn't show 21, point `JAVA_HOME` at a Java 21 install first, e.g. on a Mac with Homebrew:
`export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`.

Optional settings go in `.env` (copy `.env.example`): `PORT`, `DB_PATH`, `CORS_ORIGINS`.

## Using it from the web app

The web app sends `/api` requests to its Vite dev server, which forwards them to `API_URL`. The default is
`http://localhost:4200`; set that value in `electric-wealth-web/.env` if you need to override it.

Restart `npm run dev` after changing it.

## Commands

| Command | What it does | Node equivalent |
|---|---|---|
| `./mvnw spring-boot:run` | Start the API | `npm run dev` |
| `./mvnw test` | Run the tests, including the contract checks | `npm run verify` |
| `./mvnw test -Dtest=OpenApiSpecTest -Dopenapi.write=true` | Regenerate `openapi.json` | `npm run gen:openapi` |
| `./mvnw package` then `java -jar target/electric-wealth-api-0.1.0.jar` | Build and run a standalone jar | `npm run build` / `npm start` |

The tests build their own copy of the database with `electric-wealth-db`'s reset script, so they never touch your
data. They need `node` on the `PATH`.

## How it maps to the Node API

| Node API (`electric-wealth-api`) | This repo |
|---|---|
| `src/config.ts`, `.env` | `application.yml`, `config/AppProperties`, `.env` |
| `src/db/connection.ts` | `config/DatabaseConfig` (sqlite-jdbc + HikariCP) |
| `src/repositories/*.ts` | `repository/*Repository` (plain SQL with Spring's `JdbcClient`) |
| `src/schemas/*.ts` (Zod) | `dto/*Dtos` (Java records + Bean Validation annotations), one file per domain |
| Zod enums in `schemas/common.ts` | `dto/ApiEnums` (JSON values set with `@JsonProperty`) |
| `src/routes/*.ts` | `web/*Controller` |
| `authMiddleware` | Spring Security: `security/SecurityConfig` + `BearerTokenFilter` |
| `traceMiddleware`, `requestLogMiddleware` | `web/TraceFilter` |
| `rateLimitMiddleware` | `web/RateLimitFilter` |
| `sendError`, `errorHandler`, `notFoundHandler` | `error/ApiException` + `error/GlobalExceptionHandler` |
| `zod-to-openapi` registry, `errors(400, 401)` | springdoc annotations, `@ApiErrors({400, 401})` |
| Vitest + Supertest (`test/app.test.ts`) | JUnit 5 + MockMvc (`src/test/java`) |

A few conventions keep the JSON identical to the Node API:

- **Optional fields** are left out of responses when they're null (`spring.jackson.default-property-inclusion:
  non_null`). Fields the Node API sends as an explicit `null` use `@JsonInclude(ALWAYS)`.
- **Required fields** in the OpenAPI spec: every record field is required unless it's annotated `@Nullable`, the
  same as Zod fields are required unless they're `.optional()`. `config/RequiredRecordFieldsConverter` does this.
- **Enums** use their JSON values everywhere: in bodies (Jackson), in query parameters and in database columns
  (`config/JsonEnums`). So `?type=dividend` and a `'dividend'` column both become `TransactionType.DIVIDEND`.
- **Numbers**: whole-number doubles are written without `.0` (`48`, not `48.0`), the way JavaScript writes them
  (`config/JacksonConfig`).
- **Database rows** are mapped to records by column name (`display_name` fills `displayName`) with `repository/Rows`.
- **Timestamps** are written in JavaScript's `toISOString()` format (`2026-10-14T17:49:35.123Z`), see
  `repository/SqlValues`.
- **Errors** always use the envelope `{ "error": { "code", "message", "traceId" } }`, with the same codes and
  status codes. Validation messages are worded by Bean Validation, so the text differs from Zod's.

Known differences from the Node API, all in how unusual request bodies are rejected:

- A body over 100 kB is rejected by the Node API with 413 `PAYLOAD_TOO_LARGE`. This API has no size limit, so it reads
  the body and validates it as usual.
- A JSON body declared with a charset other than UTF-8 is rejected by the Node API with 415 `UNSUPPORTED_MEDIA_TYPE`.
  This API ignores the charset and parses the body.

`ContractParityTest` compares this API's spec with `../electric-wealth-api/openapi.json` for every endpoint
implemented here: paths, methods, status codes, parameters and request/response shapes. If it passes, the web app's
generated TypeScript types fit this API too.

## Demo only

As in the Node API, passwords are stored and compared in plain text, the MFA code is a fixed `123456`, and tokens are
random ids in the `sessions` table. Don't copy this auth into a real application.
