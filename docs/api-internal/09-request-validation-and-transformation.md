---
title: "Request Validation and Transformation"
block: reverse-eng/api/internal/request-validation-and-transformation
part: A
source_repo: electric-wealth-api-spring
source_version: 0.1.0
source_commit: 8a61cdeafb4537b16221d7aa0e9300010abd3387
extracted: 2026-10-15
---

## 9. Request Validation and Transformation

### 9.1 Validation Approach

Three tiers, all producing `400 VALIDATION_ERROR` with message `Invalid request. <field>: <message>[; …]`:

1. **Deserialisation** (Jackson): malformed JSON, wrong types, unknown enum values → `HttpMessageNotReadableException` → `"<path>: invalid value"` or `"(root): the request body could not be read as JSON"`.
2. **Bean Validation** (`spring-boot-starter-validation`): `@Valid @RequestBody` records → `MethodArgumentNotValidException`; constraints on `@RequestParam`/`@PathVariable` (Spring MVC built-in method validation) → `HandlerMethodValidationException`.
3. **Controller rules** for cross-field or time-relative checks → `ApiException.validation("field: message")`.

Messages come from Bean Validation's defaults unless overridden with `message = …`, so wording differs from the Node
API's Zod messages ([README.md](../../README.md)).

### 9.2 Writing a Request DTO with Validation

```java
// GoalDtos.java
public record GoalCreateRequest(
        @NotNull @Size(min = 1, max = 60) String name,
        @NotNull GoalCategory category,
        @NotNull @Positive @Max(MAX_AMOUNT) Long targetAmount,
        @PositiveOrZero @Max(MAX_AMOUNT) @Schema(description = "Defaults to 0") @Nullable Long currentAmount,
        @NotNull @Pattern(regexp = "CAD", message = "must be \"CAD\"") @Schema(allowableValues = "CAD") String currency,
        @NotNull @Schema(example = "2027-06-30") LocalDate targetDate) {

    public GoalCreateRequest {
        name = Text.trim(name);      // trimming happens before validation
    }
}
```

Rules:

- Use boxed types (`Long`, `Integer`, `Boolean`) with `@NotNull` for required numeric/boolean fields so a missing field is reported, not defaulted to 0/false.
- Mark optional fields `@Nullable` (jspecify) so the spec does not list them as required.
- Trim strings in the compact constructor with `Text.trim` (equivalent of Zod `.trim()`).
- Literal constants: `@Pattern(regexp = "<value>", message = "must be \"<value>\"")` + `@Schema(allowableValues = …)` (`grantType`, `currency`, `country`, appointment `status`).
- Nested objects/lists: `@Valid` on the component (`ProfileAddressUpdate.address`, `RegistrationCompleteRequest.consents`, `List<@Valid SecurityAnswer>`).
- Need to tell "absent" from "null"? Use a class with a `@JsonSetter` that records the field was sent (`AccountPatchRequest.nicknameSent`).

Notable constraints by DTO:

| DTO | Constraints |
|---|---|
| `TokenRequest` | `grantType` = `password`; `username` `@NotBlank @Email`; `password` `@NotEmpty` |
| `MfaVerifyRequest` | `challengeId`, `code` `@NotNull`; `method` ∈ `sms`/`email` |
| `RegistrationStartRequest` | `email` `@Email`, ≤254; `password` 8–72 chars, a letter and a digit |
| `VerifyIdentityRequest` | `accountNumber` `^\d{10}$`; `postalCode` ≤7; `dateOfBirth` ISO date |
| `SecurityAnswersRequest` | exactly 2 answers; each answer 2–100 chars |
| `TrustedContactRequest` | `name` 1–80, `relationship` 1–40, `phone` ≤30, `email` `@Email` |
| `RegistrationCompleteRequest` | `consents.terms/privacy/electronicDelivery` all `@AssertTrue` |
| `AccountOpenRequest` | `type` ∈ `OpenableAccountType` (TFSA, RRSP, FHSA, NON_REGISTERED); `displayName` 1–40; `nickname` ≤40 |
| `AccountPatchRequest` | `nickname` ≤40 or null; `hidden` boolean |
| `AddressInput` | `line1` 1–100; `line2` ≤100; `city` 1–60; `province` enum; `postalCode` `^[A-Za-z]\d[A-Za-z] ?\d[A-Za-z]\d$`; `country` = `Canada` |
| `AppointmentCreateRequest` | `meetingType` enum; `date` ISO date; `time` `^([01]\d|2[0-3]):[0-5]\d$`; `notes` ≤500 |
| `AppointmentPatchRequest` | `date`, `time` (same pattern), `status` = `cancelled` — combination rules in controller |
| `AdvisorMessageCreateRequest` | `text` 1–2000 after trim |
| `EducationProgressRequest` | `progressPct` 0–100 |
| `DocumentShareRequest` | `recipient` `@NotBlank @Email` |

Controller-level rules:

| Where | Rule | Message |
|---|---|---|
| `GoalController.create` | `targetDate` after today (UTC) | `targetDate: Must be a future date` |
| `GoalController.create` | `currentAmount ≤ targetAmount` | `currentAmount: currentAmount cannot exceed targetAmount` |
| `AdvisorController.update` | `date` and `time` together | `time: date and time must be changed together` |
| `AdvisorController.update` | something to change | `(root): Nothing to change` |
| `AdvisorController.update` | not both reschedule and cancel | `(root): Reschedule or cancel, not both` |
| `RegistrationController.saveSecurityAnswers` | two different questions | `answers: Choose two different questions` |
| `RegistrationController.saveSecurityAnswers` | question ids exist | `Unknown question: <ids>` |
| `ApiParams.symbol` | `^[A-Z0-9.]{1,12}$` after trim + upper-case | `symbol: Not a valid symbol` |

### 9.3 Query Parameter DTOs

There are no query-parameter DTOs; parameters are bound individually:

- Paging: `@RequestParam(defaultValue = "1") @Min(1) int page`, `@RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize`.
- Enum filters: `@RequestParam(required = false) @Nullable <Enum> x`, converted by JSON value through `JsonEnums.CONVERTER_FACTORY` (registered in `WebConfig`). An empty string converts to `null`; an unknown value raises `MethodArgumentTypeMismatchException` → `"<name>: invalid value"`.
- Free-text: `@Size(max = 60)` on glossary `q`, `@Size(max = 40)` on market search `q`; both stripped before use.
- `Accept-Language` header: read as a raw string; `Language.fromHeader` returns `FR_CA` if it starts with `fr`, else `EN_CA`.

### 9.4 Response DTOs / Serialization

- Records serialised by Jackson 3. `spring.jackson.default-property-inclusion: non_null` omits null fields; fields the Node API sends as explicit `null` use `@JsonInclude(ALWAYS)` (`AccountSummary.nickname`, `contributionRoom`, `Document.accountId`).
- `JacksonConfig` writes whole-number `Double`/`double` values as integers when `|d| < 1e15` (`48`, not `48.0`).
- Money is `long` minor units (cents). Percentages are `double`.
- Timestamps are strings formatted by `SqlValues` (ISO-8601 UTC; millisecond precision for sessions, second precision for most tables).
- Enums serialise to their `@JsonProperty` value.

### 9.5 Custom Validators

None (no `ConstraintValidator` implementations). Custom checks use the controller rules above, or `ApiParams.symbol`.

### 9.6 File Upload Validation

Not applicable — no multipart endpoints. Document "download" returns a generated base64 PDF stub.
