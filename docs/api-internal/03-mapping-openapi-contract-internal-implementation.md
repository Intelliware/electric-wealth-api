---
title: "Mapping: OpenAPI Contract → Internal Implementation"
block: reverse-eng/api/internal/mapping-openapi-contract-internal-implementation
part: A
source_repo: electric-wealth-api-spring
source_version: 0.1.0
source_commit: 8a61cdeafb4537b16221d7aa0e9300010abd3387
extracted: 2026-10-15
---

## 3. Mapping: OpenAPI Contract → Internal Implementation

The spec is **generated from the code** by springdoc at runtime (`GET /openapi.json`) and **committed** as
[`openapi.json`](../../openapi.json). `OpenApiSpecTest.committedSpecIsUpToDate` fails the build if the two differ.
Regenerate with `./mvnw test -Dtest=OpenApiSpecTest -Dopenapi.write=true`.

### 3.1 Mapping Convention

| OpenAPI element | Comes from |
|---|---|
| `info`, `servers`, `components.securitySchemes.bearerAuth` | `OpenApiConfig.openApi(...)` bean |
| `tags` | `@Tag(name = "…")` on each controller class (Auth, Registration, Health, Accounts, Dashboard, Goals, Documents, Profile, Notifications, Advisor, Education, Market) |
| Path + method | `@RequestMapping` on the class + `@GetMapping`/`@PostMapping`/`@PutMapping`/`@PatchMapping`/`@DeleteMapping` on the method |
| `summary` / `description` | `@Operation(summary, description)` |
| Success response code + description | `@ApiResponse(responseCode, description)`; `@ResponseStatus(HttpStatus.CREATED/NO_CONTENT)` sets the actual status |
| Error responses | `@ApiErrors({400, 401, …})` → `OpenApiConfig.apiErrorsCustomizer` adds each code with a `$ref: ErrorEnvelope` body and a fixed description table (400, 401, 404, 409, 422, 429) |
| `ErrorEnvelope` schema | `OpenApiConfig.errorEnvelopeSchema` registers it explicitly |
| `security: [{bearerAuth: []}]` | `@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)` on the controller class |
| Parameters | `@PathVariable`, `@RequestParam` (`defaultValue`, `required`), `@Min`/`@Max`/`@Size`; `@Parameter(example, description)` |
| `Accept-Language` header parameter | `@ApiParams.AcceptLanguage` meta-annotation (schema `Language`: `en-CA` / `fr-CA`) |
| Request / response schemas | Java records in `dto/*Dtos.java`; schema name = simple record name |
| `required` in schemas | `RequiredRecordFieldsConverter`: every record component is required unless annotated `@Nullable` (jspecify) |
| Nullable fields (`type: [string, null]`) | `@Schema(types = {"string","null"})` together with `@JsonInclude(ALWAYS)` |
| Enums | `ApiEnums.*` with `@Schema(enumAsRef = true)`; values from `@JsonProperty` |
| Literal constants (`grantType: password`, `currency: CAD`, `expiresIn: 900`) | `@Schema(allowableValues = …)` |

### 3.2 Concrete Example

`GET /api/wealth/accounts/{id}/transactions`:

```text
openapi.json                                   Source
-------------------------------------------    ----------------------------------------------------------
paths./api/wealth/accounts/{id}/transactions   AccountController (@RequestMapping("/api/wealth/accounts"))
  get                                            @GetMapping("/{id}/transactions") transactions(...)
  tags: [Accounts]                               @Tag(name = "Accounts")
  security: bearerAuth                           @SecurityRequirement(name = BEARER_AUTH)
  parameters
    id (path)                                    @Parameter(example = "tfsa") @PathVariable String id
    page (query, default 1, min 1)               @RequestParam(defaultValue = "1") @Min(1) int page
    pageSize (query, default 20, 1..100)         @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize
    type (query, TransactionType)                @RequestParam(required = false) @Nullable TransactionType type
  responses
    200 → TransactionsResponse                   return type AccountDtos.TransactionsResponse
    400/401/404 → ErrorEnvelope                  @ApiErrors({ 400, 401, 404 })
```

Runtime path: `AccountController.transactions` → `AccountRepository.find` (404 check) →
`listPostedTransactions` (paged, optional `type` filter) + `listScheduledTransactions`.

### 3.3 How DTOs Relate to OpenAPI Schemas

- Each `record` nested in a `*Dtos` holder class becomes `components.schemas.<RecordName>` (e.g. `AccountDtos.AccountSummary` → `AccountSummary`). Holder classes themselves are never exposed.
- Response records are **separate** from database rows: repositories map rows straight into response records with `Rows.of(Record.class)` when the column names line up, or into private `*Row` records first when they do not (e.g. `ProfileRepository.ProfileRow`, `MarketRepository.SecurityRow`).
- `AccountPatchRequest` is a class with getters; its `nickname` is typed `string | null` so clients can send `null` to clear it.
- Request records trim strings in their compact constructors (`Text.trim`) — this is invisible in the spec.
- Validation annotations surface as schema constraints (`minLength`, `maxLength`, `pattern`, `minimum`, `maximum`, `format: email`).
- `ContractParityTest` compares, per operation, parameters, request body shape, response shapes per status, and whether `security` is present against `../electric-wealth-api/openapi.json` (skipped if that repo is not cloned). Descriptions, examples and formats are ignored.
