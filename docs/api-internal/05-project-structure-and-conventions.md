---
title: "Project Structure and Conventions"
block: reverse-eng/api/internal/project-structure-and-conventions
part: A
source_repo: electric-wealth-api-spring
source_version: 0.1.0
source_commit: 8a61cdeafb4537b16221d7aa0e9300010abd3387
extracted: 2026-10-15
---

## 5. Project Structure and Conventions

### 5.1 Directory Structure

```text
electric-wealth-api-spring/
├── pom.xml                      Maven build (Boot parent 4.1.1, Java 21)
├── mvnw, mvnw.cmd, .mvn/        Maven wrapper
├── .env.example                 Optional overrides: PORT, DB_PATH, CORS_ORIGINS
├── openapi.json                 Committed contract (generated; checked by OpenApiSpecTest)
├── README.md
├── docs/api-internal/           This documentation (Part A)
└── src/
    ├── main/
    │   ├── java/com/electricwealth/api/
    │   │   ├── ElectricWealthApplication.java   @SpringBootApplication + @ConfigurationPropertiesScan
    │   │   ├── config/      AppProperties, DatabaseConfig, JacksonConfig, JsonEnums, WebConfig,
    │   │   │                OpenApiConfig, ApiErrors, RequiredRecordFieldsConverter
    │   │   ├── dto/         <Domain>Dtos (records), ApiEnums, Text, HealthResponse
    │   │   ├── error/       ApiException, ErrorEnvelope, ErrorResponses, GlobalExceptionHandler
    │   │   ├── repository/  <Domain>Repository ×12, PageResult, Rows, SqlValues
    │   │   ├── security/    SecurityConfig, BearerTokenFilter, BearerTokens, AuthenticatedUser
    │   │   └── web/         <Domain>Controller ×12, TraceFilter, RateLimitFilter, TraceIds, ApiParams, Labels
    │   └── resources/application.yml
    └── test/java/com/electricwealth/api/
        ApiTestBase + 6 feature test classes + OpenApiSpecTest + ContractParityTest
```

Domain → files:

| Domain | Controller | DTOs | Repository |
|---|---|---|---|
| Auth | `AuthController` | `AuthDtos` | `SessionRepository`, `UserRepository` |
| Registration | `RegistrationController` | `RegistrationDtos` | `RegistrationRepository`, `SessionRepository` |
| Health | `HealthController` | `HealthResponse` | — |
| Dashboard | `DashboardController` | `DashboardDtos` | `DashboardRepository` |
| Accounts | `AccountController` | `AccountDtos` | `AccountRepository` |
| Goals | `GoalController` | `GoalDtos` | `GoalRepository` |
| Documents | `DocumentController` | `DocumentDtos` | `DocumentRepository` |
| Profile / preferences | `ProfileController` | `ProfileDtos` | `ProfileRepository` |
| Notifications | `NotificationController` | `NotificationDtos` | `NotificationRepository` |
| Advisor | `AdvisorController` | `AdvisorDtos` | `AdvisorRepository` |
| Education | `EducationController` | `EducationDtos` | `EducationRepository` |
| Market | `MarketController` | `MarketDtos` | `MarketRepository` |

### 5.2 Naming Conventions

| Thing | Convention | Example |
|---|---|---|
| Controller | `<Domain>Controller`, `@RestController`, one `@Tag` | `GoalController` |
| DTO holder | `<Domain>Dtos` final class with private constructor holding nested records | `GoalDtos.GoalCreateRequest` |
| Request record | `<Thing>Request` / `<Thing>CreateRequest` / `<Thing>PatchRequest` / `<Thing>Update` | `AppointmentPatchRequest`, `PreferencesUpdate` |
| Response record | `<Thing>Response`, `<Things>ListResponse`, or the resource name | `GoalsListResponse`, `Goal` |
| Repository | `<Domain>Repository`, `@Repository` | `MarketRepository` |
| Private row records | `<Thing>Row` / `<Thing>Record` inside the repository | `SecurityRow`, `SessionRecord` |
| 404 helpers | `<thing>Or404(...)` private method in the controller | `advisorOr404` |
| Error codes | `UPPER_SNAKE_CASE` strings | `SLOT_UNAVAILABLE` |
| Generated IDs | `<prefix>-` + first 8 chars of a random UUID | `goal-1a2b3c4d`, `appt-…`, `msg-…`, `acct-…`, `reg-…`, `u-…` |
| Tokens | `mock-at-<uuid>`, `mock-rt-<uuid>` | `SessionRepository.create` |
| JSON field names | camelCase record component names | `displayName` |
| DB columns | snake_case; mapped to camelCase by `Rows` / `DataClassRowMapper` | `display_name` → `displayName` |
| Enum JSON values | `@JsonProperty` on the constant, or the constant name | `@JsonProperty("in_person") IN_PERSON`, `TFSA` |

### 5.3 Import Conventions and Path Aliases

- Single root package `com.electricwealth.api`; no path aliases (Java).
- Import order in source: `java.*`, project (`com.electricwealth…`), third-party (`io.swagger…`, `jakarta…`), `org.*`, then `tools.jackson…` — groups separated by blank lines.
- Static imports used for shared helpers: `SessionRepository.TOKEN_TTL_SECONDS`, `SqlValues.offset`, `SqlValues.toInt`.
- Nullability annotations come from `org.jspecify.annotations.Nullable` (drives `RequiredRecordFieldsConverter`).
- Jackson 3 (`tools.jackson.*`) for databind; Jackson 2 annotations (`com.fasterxml.jackson.annotation.*`) for `@JsonProperty`, `@JsonInclude`, `@JsonSetter`, `@JsonIgnore`.
