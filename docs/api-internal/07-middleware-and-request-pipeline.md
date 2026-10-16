---
title: "Middleware and Request Pipeline"
block: reverse-eng/api/internal/middleware-and-request-pipeline
part: A
source_repo: electric-wealth-api-spring
source_version: 0.1.0
source_commit: 8a61cdeafb4537b16221d7aa0e9300010abd3387
extracted: 2026-10-15
---

## 7. Middleware and Request Pipeline

### 7.1 Pipeline Diagram

```mermaid
sequenceDiagram
    participant C as Client
    participant T as TraceFilter (@Order HIGHEST_PRECEDENCE)
    participant R as RateLimitFilter (@Order HIGHEST_PRECEDENCE+10)
    participant S as Spring Security chain
    participant B as BearerTokenFilter
    participant D as DispatcherServlet
    participant H as Controller
    participant G as GlobalExceptionHandler

    C->>T: request
    T->>T: traceId = "trace-" + 8 hex; set attribute, X-Trace-Id header, MDC
    T->>R: chain
    R->>R: per-IP bucket; count > limit → 429 RATE_LIMITED (stop)
    R->>S: chain
    S->>S: CORS (CorsConfigurationSource)
    S->>B: before UsernamePasswordAuthenticationFilter
    B->>B: Authorization: Bearer → sessions.findActive → mfaVerified? → set AuthenticatedUser
    S->>S: authorizeHttpRequests: PROTECTED paths need authentication → else 401 UNAUTHORIZED (stop)
    S->>D: chain
    D->>D: bind params / body, Bean Validation (enum converters from WebConfig)
    D->>H: handler(@AuthenticationPrincipal AuthenticatedUser, ...)
    H-->>G: ApiException / validation / other exceptions
    G-->>C: ErrorEnvelope with status
    H-->>C: 2xx JSON (Jackson, non_null, JS-style numbers)
    T->>T: finally: log "METHOD URI STATUS Nms trace=…" (if ew.log-requests), clear MDC
```

Notes:

- Errors produced **before** Spring MVC (rate limit, authentication entry point) are written by `ErrorResponses.write`, which uses the same `ErrorEnvelope` and the trace id from the request attribute.
- Spring Security's filter chain is registered by Boot's `DelegatingFilterProxy` at its default order, which is after both `@Order(HIGHEST_PRECEDENCE…)` filters — so rate limiting happens before authentication (Javadoc on `RateLimitFilter`: "Runs before authentication").

### 7.2 Global Middleware

| Order | Component | Source | Behaviour |
|---|---|---|---|
| 1 | `TraceFilter` | [TraceFilter.java](../../src/main/java/com/electricwealth/api/web/TraceFilter.java) | Assigns `trace-xxxxxxxx` (first 8 chars of a UUID), sets request attribute `TraceIds.ATTRIBUTE`, response header `X-Trace-Id`, MDC `traceId`. After the chain, logs one INFO line to logger `requests` when `ew.log-requests` is true. |
| 2 | `RateLimitFilter` | [RateLimitFilter.java](../../src/main/java/com/electricwealth/api/web/RateLimitFilter.java) | Fixed 60-second window per `request.getRemoteAddr()`; when the count exceeds `ew.rate-limit-per-minute` (300) returns `429 RATE_LIMITED` "Too many requests. Please try again later." Applies to every path, including `/health` and `/docs`. |
| 3 | Spring Security chain | [SecurityConfig.java](../../src/main/java/com/electricwealth/api/security/SecurityConfig.java) | Stateless; CSRF, HTTP Basic, form login, logout, request cache disabled; CORS enabled; `BearerTokenFilter` before `UsernamePasswordAuthenticationFilter`; `PROTECTED` paths `.authenticated()`, everything else `.permitAll()`; entry point writes `401 UNAUTHORIZED`. |
| 4 | `GlobalExceptionHandler` | [GlobalExceptionHandler.java](../../src/main/java/com/electricwealth/api/error/GlobalExceptionHandler.java) | `@RestControllerAdvice` translating exceptions to the envelope (see [10-error-handling-and-problem-details.md](10-error-handling-and-problem-details.md)). |
| — | Converters | `WebConfig`, `JacksonConfig` | Enum conversion by JSON value for query/path params; whole-number doubles written as integers. |

CORS (`SecurityConfig.corsConfigurationSource`): origins from `ew.cors-origins`; methods GET, HEAD, PUT, PATCH, POST,
DELETE; all request headers allowed; `X-Trace-Id` exposed; registered for `/**`. Credentials are not enabled.

### 7.3 How to Add New Middleware

1. Cross-cutting, independent of auth (e.g. a header): write a `@Component` `OncePerRequestFilter` with an explicit `@Order` relative to `TraceFilter` (0) and `RateLimitFilter` (+10). Use `ErrorResponses.write(...)` to reject requests so the envelope and trace id stay consistent.
2. Auth-dependent: add it inside the security chain in `SecurityConfig.securityFilterChain` with `addFilterBefore/After`, and do **not** annotate it `@Component`.
3. Per-controller behaviour: prefer a `HandlerInterceptor` registered in `WebConfig`, or an annotation read in the controller, over a servlet filter.
4. If it changes responses, update `openapi.json` and confirm `ContractParityTest` still passes.
