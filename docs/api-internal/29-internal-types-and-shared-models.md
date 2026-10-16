---
title: "Internal Types and Shared Models"
block: reverse-eng/api/internal/internal-types-and-shared-models
part: A
source_repo: electric-wealth-api-spring
source_version: 0.1.0
source_commit: 8a61cdeafb4537b16221d7aa0e9300010abd3387
extracted: 2026-10-15
---

## 29. Internal Types and Shared Models

#### 29.1 Type Taxonomy

| Kind | Where | Visible in spec? |
|---|---|---|
| Request records | `dto/*Dtos` (`…Request`, `…Update`, `AddressInput`, `Consents`, `SecurityAnswer`) | Yes |
| Response records | `dto/*Dtos`, `HealthResponse`, `error/ErrorEnvelope` | Yes |
| Enums | `dto/ApiEnums`, `AdvisorDtos.MessageSender`, `MarketDtos.Risk` | Yes (as `$ref` schemas) |
| Repository records | `SessionRecord`, `UserRecord`, `RegistrationRow`, `OfflineClientRow`, `PerformanceRecord`, `PageResult<T>` | No |
| Private row records | `*Row` inside repositories | No |
| Security principal | `security/AuthenticatedUser(userId, accessToken)` | No |
| Exceptions | `error/ApiException` | No (shape is `ErrorEnvelope`) |

#### 29.2 Branded / Primitive Wrapper Types

None. IDs, symbols, dates and money are plain `String`/`long`. Conventions carry the meaning (see
[12-domain-models-and-business-rules.md](12-domain-models-and-business-rules.md) § 12.2).

#### 29.3 Cross-Cutting Wrappers

| Wrapper | Shape |
|---|---|
| Paged list response | `{ items: T[], page: int, pageSize: int, total: long }` — separate record per resource (`AccountsListResponse`, `TransactionsResponse` (+ `scheduled`), `GoalsListResponse`, `DocumentsListResponse`) |
| `PageResult<T>` | Internal `(items, total)` from repositories |
| Error | `ErrorEnvelope { error: { code, message, traceId } }` |
| Step acknowledgement | `StepSavedResponse { saved: true }` (constant `SAVED`) |

#### 29.4 Boundary Translation Rules

- **HTTP → Java:** Jackson binds bodies; `JsonEnums.CONVERTER_FACTORY` binds enum query/path params by JSON value; `LocalDate` from `YYYY-MM-DD`.
- **Java → DB:** enums via `JsonEnums.value`, booleans via `SqlValues.toInt`, dates via `LocalDate.toString()`, blanks via `SqlValues.blankToNull`.
- **DB → Java:** `Rows.of` (snake_case → camelCase, 0/1 → boolean, enum by JSON value); NULL → `null` for `@Nullable` components.
- **Java → HTTP:** Jackson with `non_null`, JS-style whole numbers, enum JSON values.
- `ProfileRow` → `ProfileResponse` builds `trustedContact` only when both name and relationship are non-null.

#### 29.5 Type Reuse Rules

- Response records are reused across endpoints where the shape is identical (`Quote` for quotes and watchlist; `TokenResponse` for login and registration complete; `AccountDetail` for detail and patch).
- `AccountDetail.of(summary, holdings)` builds the detail from the summary record — extend both when adding account fields.
- Do not expose repository records in controllers' return types.

#### 29.6 Type-Safety Tools in Use

- jspecify `@Nullable` (drives `required` in the spec; no static null checker is configured in `pom.xml`).
- Bean Validation annotations.
- `ContractParityTest` / `OpenApiSpecTest` as type-level contract tests against the web app's expectations.
