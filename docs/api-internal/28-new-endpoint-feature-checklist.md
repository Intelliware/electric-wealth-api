---
title: "New Endpoint / Feature Checklist"
block: reverse-eng/api/internal/new-endpoint-feature-checklist
part: A
source_repo: electric-wealth-api-spring
source_version: 0.1.0
source_commit: 8a61cdeafb4537b16221d7aa0e9300010abd3387
extracted: 2026-10-15
---

## 28. New Endpoint / Feature Checklist

#### 28.1 Pre-Flight

- [ ] Does the Node API (`electric-wealth-api`) already have this endpoint? If so, match its contract exactly (`ContractParityTest`).
- [ ] Does it need schema changes? Make them in `electric-wealth-db/migrations/` first.

#### 28.2 Contract

- [ ] Request/response records in `dto/<Domain>Dtos.java`; optional fields `@Nullable`; explicit-null fields `@JsonInclude(ALWAYS)` + `@Schema(types = {…, "null"})`.
- [ ] Enums in `ApiEnums` with `@JsonProperty` values and `@Schema(enumAsRef = true)`.
- [ ] Money in cents (`long`), dates as ISO strings.

#### 28.3 Route & Controller

- [ ] Method on the domain controller (or a new `@RestController` with `@Tag` and `@SecurityRequirement` if protected).
- [ ] `@Operation(summary)`, `@ApiResponse(responseCode)`, `@ResponseStatus(CREATED)` for creates, `@ApiErrors({…})` listing every status the handler can return.
- [ ] `@Valid @RequestBody`; paging params with the standard defaults/bounds.
- [ ] New URL prefix? Add it to `SecurityConfig.PROTECTED` if it needs a signed-in user.

#### 28.4 Service

- [ ] There is no service layer: put cross-field rules in the controller (`ApiException.validation`), "not found" via `…Or404` helpers.

#### 28.5 Repository / Data Access

- [ ] `JdbcClient` SQL with bound parameters and `user_id = ?`.
- [ ] Map with `Rows.of(Record.class)`; write enums with `JsonEnums.value`, booleans with `SqlValues.toInt`, timestamps with `SqlValues`.
- [ ] `@Transactional` for multi-statement writes.

#### 28.6 Cross-Cutting

- [ ] Localised text? Read `Accept-Language` via `@ApiParams.AcceptLanguage` and `Language.fromHeader`.
- [ ] Sorting fixed in SQL; paging via `PageResult`.

#### 28.7 Observability

- [ ] Nothing extra needed for the request log; do not add logging of bodies or tokens.

#### 28.8 Security

- [ ] Ownership filter in SQL; 404 for other users' resources.
- [ ] Validation limits on every free-text field.

#### 28.9 Tests

- [ ] Integration test in the matching `*ApiTest` (or a new class extending `ApiTestBase`): success, each error, 401 without token, DB persistence.

#### 28.10 Documentation

- [ ] Regenerate `openapi.json`: `./mvnw test -Dtest=OpenApiSpecTest -Dopenapi.write=true`, commit it.
- [ ] Update this Part A and the KB consumer guide; log the change in [CHANGE-TRACKER.md](CHANGE-TRACKER.md).
- [ ] The web app regenerates its TypeScript types from the spec.

#### 28.11 Rollout

- [ ] No feature flags or deployment pipeline exist; coordinate with the web app and the Node API.
