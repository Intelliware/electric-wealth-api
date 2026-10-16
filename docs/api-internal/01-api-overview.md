---
title: "API Overview"
block: reverse-eng/api/internal/api-overview
part: A
source_repo: electric-wealth-api-spring
source_version: 0.1.0
source_commit: 8a61cdeafb4537b16221d7aa0e9300010abd3387
extracted: 2026-10-15
---

## 1. API Overview

### 1.1 What This API Is

- **Name:** Electric Wealth API (Spring) — Maven artifact `com.electricwealth:electric-wealth-api-spring:0.1.0` ([pom.xml](../../pom.xml))
- **Language / Runtime:** Java 21 (`<java.version>21</java.version>`)
- **Framework:** Spring Boot 4.1.1 (`spring-boot-starter-parent`) with `spring-boot-starter-webmvc`, `-validation`, `-security`, `-jdbc`; springdoc-openapi 3.1.1; sqlite-jdbc 3.53.4.0
- **Purpose:** REST backend for the Electric Wealth web app (`electric-wealth-web`). Serves sign-in (password + MFA), online enrolment, the wealth dashboard, accounts, goals, documents, profile/preferences, notifications, advisor booking and messaging, education content and market data. It is a port of the Node API (`electric-wealth-api`) and is required to return identical JSON ([README.md](../../README.md), `pom.xml` `<description>`).
- **OpenAPI spec location:** committed at [`openapi.json`](../../openapi.json) (OpenAPI 3.1.0); served at runtime at `GET /openapi.json`, Swagger UI at `GET /docs` ([application.yml](../../src/main/resources/application.yml) `springdoc.*`).
- **Size:** 49 operations across 12 controllers, 12 repositories, 13 DTO files.

### 1.2 Architecture Style

- **Architecture:** Single-module monolith, packaged by technical layer (`web`, `dto`, `repository`, `security`, `error`, `config`). Two layers do the work: controllers and repositories. There is no service layer (see [04-layered-architecture.md](04-layered-architecture.md)).
- **API style:** REST over JSON. Contract-first in effect: the committed `openapi.json` is regenerated from code and checked by `OpenApiSpecTest`; `ContractParityTest` compares it with the Node API's spec.
- **Communication:** Synchronous HTTP from the browser (via the web app's Vite dev proxy) to this API. No outbound calls, no messaging. Persistence is a local SQLite file owned by the sibling `electric-wealth-db` repo.

### 1.3 Key Architectural Decisions

| Decision | Rationale (from source comments / README) |
|---|---|
| No ORM — plain SQL through Spring's `JdbcClient` | Mirrors the Node API's hand-written repositories; schema owned by another repo ([README.md](../../README.md) mapping table). |
| No service layer — controllers call repositories directly | Port of the Node API's route handlers; business checks live in controllers and transactional multi-step writes live in repositories. |
| Schema is never created or migrated by this app | `DatabaseConfig` Javadoc: "The schema belongs to that repo, so this app never creates or migrates tables." |
| JSON must be byte-compatible with the Node API | Web app's TypeScript types are generated from the Node spec; enforced by `ContractParityTest`, `JacksonConfig` (JS-style numbers), `SqlValues` (JS `toISOString()` timestamps), `spring.jackson.default-property-inclusion: non_null`. |
| Own bearer-token filter instead of OAuth/JWT | Sessions are rows in the shared `sessions` table, so a token from the Node API works here and vice versa (`SessionRepository` Javadoc). |
| Every record field is required in the spec unless `@Nullable` | `RequiredRecordFieldsConverter` mirrors Zod's "required unless `.optional()`". |
| Single error envelope `{ "error": { code, message, traceId } }` | Same codes and statuses as the Node API (`GlobalExceptionHandler` Javadoc). |
| Demo-grade auth | Plain-text passwords, fixed MFA code `123456`, random token ids ([README.md](../../README.md) "Demo only"). |

### 1.4 Design Principles

- **Contract parity first:** any change that alters JSON shape, status codes or parameters must keep `ContractParityTest` and `OpenApiSpecTest` green.
- **Thin controllers, SQL in repositories:** controllers validate cross-field rules and map "not found" to `ApiException`; repositories hold SQL and multi-statement transactions.
- **Immutable DTOs:** request and response bodies are Java records (one exception: `AccountPatchRequest`, a class that distinguishes "field absent" from "field null").
- **Fail at the boundary:** Bean Validation on request records and parameters, plus explicit `ApiException.validation(...)` for cross-field rules; all failures become `400 VALIDATION_ERROR`.
- **User scoping in every query:** every repository query on user data filters on `user_id = ?` taken from the authenticated principal, never from the request.
