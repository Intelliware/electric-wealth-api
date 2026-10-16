---
title: "Security Implementation"
block: reverse-eng/api/internal/security-implementation
part: A
source_repo: electric-wealth-api-spring
source_version: 0.1.0
source_commit: 8a61cdeafb4537b16221d7aa0e9300010abd3387
extracted: 2026-10-15
---

## 23. Security Implementation

### 23.1 Threat Model Snapshot

The README labels the auth "Demo only". Facts relevant to any threat model:

| Area | Current state | Source |
|---|---|---|
| Password storage | Plain text in `users.password` and `registrations.password`; compared with `String.equals` | `AuthController.token`, `RegistrationRepository` |
| MFA | Fixed code `123456` per user (`users.mfa_code`); `challengeId`/`method` ignored; no attempt limit | `AuthController.verifyMfa`, `RegistrationRepository.DEMO_MFA_CODE` |
| Tokens | Random `mock-at-`/`mock-rt-` UUIDs in `sessions`; access TTL 900 s; refresh tokens rotate but have no own expiry | `SessionRepository` |
| Brute force | Only the global per-IP limit (300/min) | `RateLimitFilter` |
| Data isolation | Every user-data query filtered by `user_id` from the principal | `repository/*` |
| Security answers | Stored normalised in plain text | `RegistrationRepository.saveSecurityAnswers` |
| PII in responses | Masked phone and SIN (`phoneMasked`, `sinMasked`) as stored | `ProfileRepository` |
| Account enumeration | `POST /api/registration/start` returns 409 `EMAIL_TAKEN` for existing emails; login uses one message for unknown email and wrong password | `RegistrationController`, `AuthController` |

### 23.2 Security Middleware Chain

`TraceFilter` → `RateLimitFilter` → Spring Security (`CorsFilter` → `BearerTokenFilter` → authorization) →
MVC. See [07-middleware-and-request-pipeline.md](07-middleware-and-request-pipeline.md) and
[08-authentication-and-authorization-internals.md](08-authentication-and-authorization-internals.md).
Disabled Spring Security features: CSRF (no cookies), HTTP Basic, form login, logout handler, request cache,
HTTP session creation (`STATELESS`). `UserDetailsServiceAutoConfiguration` is excluded.

### 23.3 Secrets Management

No secrets are configured: no keys, no external credentials. `.env` (git-ignored) holds only `PORT`, `DB_PATH`,
`CORS_ORIGINS`. The demo password `Demo123!` and MFA code `123456` are documented in the spec description.

### 23.4 Transport & TLS

Plain HTTP on `server.port` (4200). No TLS configuration in this repo; the only published server is
`http://localhost:4200`.

### 23.5 Rate Limiting

`RateLimitFilter`: fixed one-minute window per client IP (`request.getRemoteAddr()`), limit `ew.rate-limit-per-minute`
(300). Over the limit → `429 RATE_LIMITED`, no `Retry-After` or rate-limit headers. Buckets are kept in memory
per instance and never evicted. Behind a reverse proxy every client would share the proxy's IP
(`X-Forwarded-For` is not read).

### 23.6 Input Validation Boundary

All input passes Bean Validation / controller rules before reaching SQL (see
[09-request-validation-and-transformation.md](09-request-validation-and-transformation.md)). SQL is always
parameterised; `LIKE` wildcards are escaped. No request body size limit is configured (README: the Node API
rejects >100 kB with 413; this API does not).

### 23.7 Output Encoding

JSON only (Jackson). The only non-JSON content is the base64 PDF stub, built from the document name with
non-printable characters replaced and `\ ( )` escaped for PDF syntax (`DocumentRepository.stubPdfBase64`).
Error messages echo the request path for unknown routes (`No route for GET /x.`) inside JSON.

### 23.8 Vulnerability Surface — What is Out of Scope

- Real credential storage, real MFA delivery and real document storage are explicitly out of scope (demo).
- File uploads, outbound HTTP, deserialisation of polymorphic types: none present.
- `/openapi.json` and `/docs` are public.
