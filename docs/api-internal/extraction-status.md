---
title: "Extraction Status — electric-wealth-api-spring"
skill: reverse-engineer-api
source_repo: electric-wealth-api-spring
source_version: 0.1.0
source_commit: 8a61cdeafb4537b16221d7aa0e9300010abd3387
source_branch: kb1
extracted: 2026-10-15
---

# Extraction Status — electric-wealth-api-spring

Tracks the reverse-engineering pass of the Spring Boot API. Every inventoried artifact maps to the documentation
location that covers it. Section numbers refer to the `NN-` prefixes of the Part A files in this folder (A) and of
the Part B2 files in `<KB>/source-docs/electric-wealth-api-spring/api-consumer/` (B2).

## Scope

| Item | Value |
|---|---|
| Repo path | `/Users/kosta/repos/em/ElectricWeatlhParent/electric-wealth-api-spring` |
| Monorepo | No (single Maven module) |
| Included | `src/main/**`, `src/test/**`, `pom.xml`, `README.md`, `.env.example`, `openapi.json` |
| Excluded | `target/`, `.mvn/` |
| Language / framework | Java 21 / Spring Boot 4.1.1 (Spring MVC, Spring Security, Spring JDBC), springdoc-openapi 3.1.1, sqlite-jdbc 3.53.4.0 |
| Consumer profile | `internal-app` (backend for `electric-wealth-web`) |
| Entry points | `ElectricWealthApplication` (server start); 12 `@RestController` classes under `web/` (HTTP routers) |
| Existing docs | `README.md`, `openapi.json` (committed, springdoc-generated, OpenAPI 3.1.0) |
| KB root | `/Users/kosta/repos/em/ElectricWeatlhParent/electric-wealth-knowledge-base` |

## Decision gates (composition `include-if` flags)

| Flag | Value | Evidence |
|---|---|---|
| `has_openapi_contract` | true | `openapi.json` committed; `OpenApiSpecTest` keeps it in sync |
| `has_persistence` | true | `repository/*Repository` use `JdbcClient` against SQLite |
| `has_caching` | false | No `@Cacheable` / cache manager in `src/` |
| `has_background_jobs` | false | No `@Scheduled` / `@Async` in `src/` |
| `has_inter_service_communication` | false | No `RestClient` / `WebClient` / `RestTemplate` in `src/` |
| `has_external_integrations` | false | `project.toml` and source: no outbound integrations |
| `has_feature_flags` | false | No flag provider or `@ConditionalOnProperty` in `src/` |
| `api_emits_webhooks` | false | No outbound calls |
| `api_has_sdks` | false | No SDK published; the web app generates its own TS types from `openapi.json` |
| `api_publishes_sla` | false | Nothing in repo |
| `api_has_support_channels` | false | Nothing in repo |

## Endpoint inventory (49 operations)

Counted from the `@GetMapping` / `@PostMapping` / `@PutMapping` / `@PatchMapping` / `@DeleteMapping` handler
methods in `web/*Controller.java`, and reconciled against the 49 operations in the committed `openapi.json`
(matches `README.md`: "49 endpoints").

| # | Method | Path | Controller method | Part A | Part B1 | Part B2 | Status |
|---|---|---|---|---|---|---|---|
| 1 | GET | `/health` | `HealthController.health` | 21, 25 | ✅ | 04, 05 | ✅ |
| 2 | POST | `/api/auth/token` | `AuthController.token` | 08 | ✅ | 02, 04, 05 | ✅ |
| 3 | POST | `/api/auth/mfa/verify` | `AuthController.verifyMfa` | 08 | ✅ | 02, 04, 05 | ✅ |
| 4 | POST | `/api/auth/refresh` | `AuthController.refresh` | 08 | ✅ | 02, 04, 05 | ✅ |
| 5 | POST | `/api/auth/logout` | `AuthController.logout` | 08 | ✅ | 02, 04, 05 | ✅ |
| 6 | POST | `/api/registration/start` | `RegistrationController.start` | 12, 31 | ✅ | 04, 05 | ✅ |
| 7 | POST | `/api/registration/verify-identity` | `RegistrationController.verifyIdentity` | 12, 31 | ✅ | 04, 05 | ✅ |
| 8 | GET | `/api/registration/security-questions` | `RegistrationController.securityQuestions` | 12 | ✅ | 04, 05 | ✅ |
| 9 | POST | `/api/registration/security-questions` | `RegistrationController.saveSecurityAnswers` | 12, 31 | ✅ | 04, 05 | ✅ |
| 10 | POST | `/api/registration/trusted-contact` | `RegistrationController.saveTrustedContact` | 12, 31 | ✅ | 04, 05 | ✅ |
| 11 | POST | `/api/registration/complete` | `RegistrationController.complete` | 11, 12, 31 | ✅ | 04, 05 | ✅ |
| 12 | GET | `/api/wealth/dashboard` | `DashboardController.dashboard` | 12, 15 | ✅ | 04, 05 | ✅ |
| 13 | GET | `/api/wealth/accounts` | `AccountController.list` | 13, 15 | ✅ | 04, 05, 08 | ✅ |
| 14 | POST | `/api/wealth/accounts` | `AccountController.open` | 12 | ✅ | 04, 05 | ✅ |
| 15 | GET | `/api/wealth/accounts/{id}` | `AccountController.detail` | 12 | ✅ | 04, 05 | ✅ |
| 16 | PATCH | `/api/wealth/accounts/{id}` | `AccountController.update` | 09, 12 | ✅ | 04, 05 | ✅ |
| 17 | GET | `/api/wealth/accounts/{id}/transactions` | `AccountController.transactions` | 13, 15 | ✅ | 04, 05, 08, 09 | ✅ |
| 18 | GET | `/api/wealth/accounts/{id}/performance` | `AccountController.performance` | 12, 27 | ✅ | 04, 05 | ✅ |
| 19 | GET | `/api/wealth/goals` | `GoalController.list` | 13 | ✅ | 04, 05, 08 | ✅ |
| 20 | POST | `/api/wealth/goals` | `GoalController.create` | 12 | ✅ | 04, 05 | ✅ |
| 21 | GET | `/api/wealth/documents` | `DocumentController.list` | 13, 15 | ✅ | 04, 05, 08, 09 | ✅ |
| 22 | GET | `/api/wealth/documents/{id}` | `DocumentController.detail` | 12 | ✅ | 04, 05 | ✅ |
| 23 | POST | `/api/wealth/documents/{id}/share` | `DocumentController.share` | 12 | ✅ | 04, 05 | ✅ |
| 24 | GET | `/api/profile` | `ProfileController.profile` | 12 | ✅ | 04, 05 | ✅ |
| 25 | PUT | `/api/profile/address` | `ProfileController.updateAddress` | 09, 12 | ✅ | 04, 05 | ✅ |
| 26 | GET | `/api/preferences` | `ProfileController.preferences` | 12 | ✅ | 04, 05 | ✅ |
| 27 | PUT | `/api/preferences` | `ProfileController.updatePreferences` | 11, 12 | ✅ | 04, 05 | ✅ |
| 28 | GET | `/api/notifications` | `NotificationController.list` | 12 | ✅ | 04, 05 | ✅ |
| 29 | POST | `/api/notifications/{id}/read` | `NotificationController.markRead` | 12 | ✅ | 04, 05 | ✅ |
| 30 | POST | `/api/notifications/read-all` | `NotificationController.markAllRead` | 12 | ✅ | 04, 05 | ✅ |
| 31 | GET | `/api/education/content` | `EducationController.content` | 12, 15 | ✅ | 04, 05, 09 | ✅ |
| 32 | GET | `/api/education/glossary` | `EducationController.glossary` | 12, 15 | ✅ | 04, 05, 09 | ✅ |
| 33 | POST | `/api/education/progress` | `EducationController.saveProgress` | 15 | ✅ | 04, 05 | ✅ |
| 34 | GET | `/api/advisor/availability` | `AdvisorController.availability` | 12 | ✅ | 04, 05 | ✅ |
| 35 | GET | `/api/advisor/appointments` | `AdvisorController.appointments` | 12 | ✅ | 04, 05 | ✅ |
| 36 | POST | `/api/advisor/appointments` | `AdvisorController.book` | 11, 12 | ✅ | 04, 05 | ✅ |
| 37 | PATCH | `/api/advisor/appointments/{id}` | `AdvisorController.update` | 11, 12 | ✅ | 04, 05 | ✅ |
| 38 | GET | `/api/advisor/messages` | `AdvisorController.messages` | 12 | ✅ | 04, 05 | ✅ |
| 39 | POST | `/api/advisor/messages` | `AdvisorController.sendMessage` | 12 | ✅ | 04, 05 | ✅ |
| 40 | GET | `/api/market/quote` | `MarketController.quotes` | 15 | ✅ | 04, 05, 09 | ✅ |
| 41 | GET | `/api/market/securities/{symbol}` | `MarketController.security` | 09 | ✅ | 04, 05 | ✅ |
| 42 | GET | `/api/market/history` | `MarketController.history` | 15 | ✅ | 04, 05 | ✅ |
| 43 | GET | `/api/market/search` | `MarketController.search` | 15 | ✅ | 04, 05, 09 | ✅ |
| 44 | GET | `/api/market/news` | `MarketController.news` | 15 | ✅ | 04, 05 | ✅ |
| 45 | GET | `/api/market/roundup` | `MarketController.roundup` | 15 | ✅ | 04, 05 | ✅ |
| 46 | GET | `/api/market/baskets` | `MarketController.baskets` | 15 | ✅ | 04, 05 | ✅ |
| 47 | GET | `/api/market/watchlist` | `MarketController.watchlist` | 15 | ✅ | 04, 05 | ✅ |
| 48 | PUT | `/api/market/watchlist/{symbol}` | `MarketController.addToWatchlist` | 15 | ✅ | 04, 05 | ✅ |
| 49 | DELETE | `/api/market/watchlist/{symbol}` | `MarketController.removeFromWatchlist` | 15, 27 | ✅ | 04, 05 | ✅ |

Also served at runtime but not part of the contract: `GET /openapi.json` (springdoc) and `GET /docs` (Swagger UI).

## Infrastructure inventory

| Artifact | Source | Part A section |
|---|---|---|
| Application entry point | `ElectricWealthApplication.java` | 01, 06, 25 |
| Configuration properties | `config/AppProperties.java`, `application.yml`, `.env.example` | 02 |
| DataSource | `config/DatabaseConfig.java` | 02, 14 |
| JSON serialisation | `config/JacksonConfig.java`, `config/JsonEnums.java`, `config/WebConfig.java` | 09, 29 |
| OpenAPI generation | `config/OpenApiConfig.java`, `config/ApiErrors.java`, `config/RequiredRecordFieldsConverter.java` | 03 |
| Security chain | `security/SecurityConfig.java`, `security/BearerTokenFilter.java`, `security/BearerTokens.java`, `security/AuthenticatedUser.java` | 07, 08, 23 |
| Servlet filters | `web/TraceFilter.java`, `web/RateLimitFilter.java`, `web/TraceIds.java` | 07, 21, 23 |
| Error handling | `error/ApiException.java`, `error/ErrorEnvelope.java`, `error/ErrorResponses.java`, `error/GlobalExceptionHandler.java` | 10 |
| Shared web helpers | `web/ApiParams.java`, `web/Labels.java` | 09, 13 |
| DTOs | `dto/*Dtos.java`, `dto/ApiEnums.java`, `dto/Text.java`, `dto/HealthResponse.java` | 09, 12, 29 |
| Repositories (12) + helpers (`PageResult`, `Rows`, `SqlValues`) | `repository/*.java` | 11, 14, 15 |
| Tests (9 classes, 67 test methods) | `src/test/java/com/electricwealth/api/*.java` | 22 |

## Section status

| Part | Files | Status |
|---|---|---|
| A | 01–16, 21–25, 27–31 (26 files) | ✅ Extracted |
| A | 17 caching, 18 background jobs, 19 inter-service, 20 external integrations, 26 feature flags | ⛔ Gated out (flag false) |
| B1 | `<KB>/source-docs/electric-wealth-api-spring/openapi.yaml` | ✅ Converted from committed `openapi.json` |
| B2 | 01–11, 14, 15 (13 files) | ✅ Extracted |
| B2 | 12 webhooks, 13 SDKs, 16 status/SLA, 17 support | ⛔ Gated out (flag false) |
