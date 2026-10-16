---
title: "Error Handling and Problem Details"
block: reverse-eng/api/internal/error-handling-and-problem-details
part: A
source_repo: electric-wealth-api-spring
source_version: 0.1.0
source_commit: 8a61cdeafb4537b16221d7aa0e9300010abd3387
extracted: 2026-10-15
---

## 10. Error Handling and Problem Details

### 10.1 Error Strategy

- One envelope for every error, matching the Node API: not RFC 7807 Problem Details.
- Controllers throw `ApiException(status, code, message)`; framework exceptions are mapped in `GlobalExceptionHandler`.
- Filters that run before MVC (rate limit, security entry point) write the same envelope through `ErrorResponses.write`.
- Unknown routes **and** wrong methods on known routes are both `404 NOT_FOUND` (Node parity).
- Anything unexpected → `500 INTERNAL_ERROR` "Something went wrong." and an ERROR log with the trace id.

### 10.2 Standard Error Response Shape

```json
{
  "error": {
    "code": "NOT_FOUND",
    "message": "Account not found.",
    "traceId": "trace-1a2b3c4d"
  }
}
```

Defined by `ErrorEnvelope(Detail error)` / `Detail(code, message, traceId)`
([ErrorEnvelope.java](../../src/main/java/com/electricwealth/api/error/ErrorEnvelope.java)). The same trace id is
also in the `X-Trace-Id` response header.

Complete code catalogue (every `new ApiException`, factory and handler in `src/main`):

| Status | Code | Raised by |
|---|---|---|
| 400 | `VALIDATION_ERROR` | `ApiException.validation`; all validation handlers in `GlobalExceptionHandler` |
| 401 | `UNAUTHORIZED` | `ApiException.unauthorized()`; `SecurityConfig` authentication entry point |
| 401 | `INVALID_CREDENTIALS` | `AuthController.token` |
| 401 | `INVALID_MFA` | `AuthController.verifyMfa` |
| 401 | `INVALID_REFRESH` | `AuthController.refresh` |
| 404 | `NOT_FOUND` | `ApiException.notFound` (accounts, performance, dashboard, documents, profile, preferences, notifications, appointments, education content, market symbols); unknown route / wrong method |
| 404 | `NO_ADVISOR` | `AdvisorController.advisorOr404` |
| 404 | `REGISTRATION_NOT_FOUND` | `RegistrationController.openOr404` |
| 409 | `EMAIL_TAKEN` | `RegistrationController.start`, `.complete` |
| 409 | `STEP_OUT_OF_ORDER` | `RegistrationController.verifiedOr409`, `.complete` |
| 409 | `ALREADY_ENROLLED` | `RegistrationController.verifyIdentity` |
| 409 | `SLOT_UNAVAILABLE` | `AdvisorController.book`, `.update` (pre-check and unique-index violation) |
| 409 | `NOT_SCHEDULED` | `AdvisorController.update` |
| 422 | `IDENTITY_MISMATCH` | `RegistrationController.verifyIdentity` (`HttpStatus.UNPROCESSABLE_CONTENT`) |
| 429 | `RATE_LIMITED` | `RateLimitFilter` |
| 500 | `INTERNAL_ERROR` | `GlobalExceptionHandler.unexpected` |

### 10.3 Custom Exception Classes

| Class | Purpose |
|---|---|
| [`ApiException`](../../src/main/java/com/electricwealth/api/error/ApiException.java) | `RuntimeException` carrying `HttpStatus status` and `String code`. Factories: `unauthorized()`, `notFound(message)`, `validation(detail)` (prefixes `"Invalid request. "`). |

`GlobalExceptionHandler` mappings:

| Exception | Result |
|---|---|
| `ApiException` | its status / code / message |
| `MethodArgumentNotValidException` | 400; `field: msg` joined by `; `; empty → `(root): invalid` |
| `HandlerMethodValidationException` | 400; per parameter `name: msg`, or per field for a `@Valid` body reported via method validation |
| `HttpMessageNotReadableException` | 400; `a.b.0.c: invalid value` from the Jackson path, else `(root): the request body could not be read as JSON` |
| `HttpMediaTypeNotSupportedException` | 400; `(root): the request body must be JSON (Content-Type: application/json)` |
| `MethodArgumentTypeMismatchException` | 400; `<param>: invalid value` |
| `MissingServletRequestParameterException` | 400; `<param>: required` |
| `NoResourceFoundException`, `HttpRequestMethodNotSupportedException` | 404 `NOT_FOUND` `No route for <METHOD> <URI>.` |
| `Exception` | 500 `INTERNAL_ERROR` |

### 10.4 Throwing Exceptions — Correct Pattern

```java
// Not found / not owned: same 404
var account = accounts.find(user.userId(), id)
        .orElseThrow(() -> ApiException.notFound("Account not found."));

// Cross-field validation
if (body.currentAmount() != null && body.currentAmount() > body.targetAmount()) {
    throw ApiException.validation("currentAmount: currentAmount cannot exceed targetAmount");
}

// Domain conflict with its own code
throw new ApiException(HttpStatus.CONFLICT, "NOT_SCHEDULED", "A cancelled appointment cannot be changed.");
```

Then list the status in `@ApiErrors({ … })` on the handler so the spec documents it. Repositories return
`Optional`/`boolean` and never throw `ApiException`.

### 10.5 Error Logging Behavior

- Only the catch-all `unexpected` handler logs, at ERROR, with the stack trace: `[trace=<id>] Unhandled error`.
- 4xx errors are not logged individually; they appear in the request log line from `TraceFilter` (status code).
- Error messages never include stack traces or SQL.
