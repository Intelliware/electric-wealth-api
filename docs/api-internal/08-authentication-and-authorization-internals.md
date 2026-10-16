---
title: "Authentication and Authorization Internals"
block: reverse-eng/api/internal/authentication-and-authorization-internals
part: A
source_repo: electric-wealth-api-spring
source_version: 0.1.0
source_commit: 8a61cdeafb4537b16221d7aa0e9300010abd3387
extracted: 2026-10-15
---

## 8. Authentication and Authorization Internals

> **Demo-grade.** Passwords are stored and compared in plain text, the MFA code is a fixed `123456` stored per user
> in `users.mfa_code`, and tokens are random ids in the `sessions` table ([README.md](../../README.md) "Demo only").

### 8.1 Authentication Flow

```mermaid
sequenceDiagram
    participant C as Client
    participant A as AuthController
    participant S as SessionRepository
    participant U as UserRepository
    C->>A: POST /api/auth/token {grantType:"password", username, password}
    A->>U: findByEmail(username); password.equals(...)
    A->>S: create(userId, mfaVerified=false)
    A-->>C: 200 {accessToken, refreshToken, tokenType:"Bearer", expiresIn:900, mfaRequired:true}
    C->>A: POST /api/auth/mfa/verify (Authorization: Bearer accessToken) {challengeId, code, method}
    A->>S: findActive(token)  (any active session, verified or not)
    A->>U: findById(userId); mfaCode.equals(code)
    A->>S: markMfaVerified(token)  (sets mfa_verified=1, extends expires_at by 900s)
    A-->>C: 200 {accessToken (same), expiresIn:900}
    C->>A: GET /api/... (Authorization: Bearer accessToken)
    Note over C,A: BearerTokenFilter: findActive + mfaVerified → AuthenticatedUser
    C->>A: POST /api/auth/refresh {refreshToken}
    A->>S: rotate(refreshToken) @Transactional: revoke old, create new (keeps mfaVerified)
    A-->>C: 200 {accessToken, refreshToken, tokenType, expiresIn}
    C->>A: POST /api/auth/logout (optional Bearer) {refreshToken?}
    A->>S: revoke(accessToken?, refreshToken?)
    A-->>C: 204
```

Token storage — `sessions` table (shared with the Node API, so tokens are interchangeable):
`access_token`, `refresh_token`, `user_id`, `mfa_verified` (0/1), `revoked` (0/1), `expires_at` (ISO millis),
`created_at`.

Facts from [SessionRepository.java](../../src/main/java/com/electricwealth/api/repository/SessionRepository.java):

- `TOKEN_TTL_SECONDS = 900`. `expires_at` is set to now + 900 s on create and again on `markMfaVerified`.
- Access token `mock-at-<UUID>`, refresh token `mock-rt-<UUID>`.
- `findActive` = row exists, `revoked = 0`, `expires_at` in the future (checked in Java via `Instant.parse`).
- `rotate` looks up `refresh_token = ? AND revoked = 0` **without an expiry check**, revokes that session, and creates a new one with the same `mfa_verified`. A refresh token therefore stays usable until it is used or revoked, even after its access token expired.
- `revoke` revokes by access token and/or by refresh token; unknown tokens are a no-op.

Controller details ([AuthController.java](../../src/main/java/com/electricwealth/api/web/AuthController.java)):

| Endpoint | Failure | Status / code |
|---|---|---|
| `/token` | Unknown email or wrong password | 401 `INVALID_CREDENTIALS` "Invalid username or password." |
| `/mfa/verify` | No/unknown/expired/revoked bearer token | 401 `UNAUTHORIZED` |
| `/mfa/verify` | Code ≠ `users.mfa_code` | 401 `INVALID_MFA` "Invalid MFA code." |
| `/refresh` | Unknown or revoked refresh token | 401 `INVALID_REFRESH` "Refresh token is invalid or expired." |
| `/logout` | — | Always 204 (body optional) |

`/mfa/verify` ignores `challengeId` and `method` (validated as present/enum, never read). It is public at the
security layer and checks the token itself because a not-yet-verified token is not accepted by `BearerTokenFilter`.

Registration (`POST /api/registration/complete`) also creates a session, with `mfaVerified = true` and
`mfaRequired: false` in the response (see [12-domain-models-and-business-rules.md](12-domain-models-and-business-rules.md)).

### 8.2 Authorization Model

- **Authentication only, no roles.** `BearerTokenFilter` creates `UsernamePasswordAuthenticationToken.authenticated(user, token, List.of())` — an empty authority list.
- **Path-based gate** in `SecurityConfig.PROTECTED`: `/api/wealth/**`, `/api/profile/**`, `/api/preferences/**`, `/api/notifications/**`, `/api/education/**`, `/api/advisor/**`, `/api/market/**` require authentication. Every other path is `permitAll()` — `/health`, `/api/auth/**`, `/api/registration/**`, `/openapi.json`, `/docs/**`.
- Because the matcher covers whole prefixes, an **unknown path under a protected prefix returns 401**, not 404, when unauthenticated (mirrors the Node API's router-level middleware).
- **Ownership** is enforced in data access: every query on user data includes `user_id = ?` bound to `AuthenticatedUser.userId()`. A resource that belongs to another user is indistinguishable from a missing one (404).

### 8.3 Applying Authorization

- Controllers receive the user via `@AuthenticationPrincipal AuthenticatedUser user` and pass `user.userId()` to repositories.
- Add `@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)` on the controller so the spec marks operations as secured.
- A new protected router prefix must be added to `SecurityConfig.PROTECTED`; otherwise its endpoints are public and `@AuthenticationPrincipal` resolves to `null` (leading to a `NullPointerException` → 500).
- Some protected endpoints do not take the principal at all (`MarketController.search`, `news`, `roundup`, `baskets`) but are still protected by the path rule.

### 8.4 Internal Service-to-Service Auth

Not applicable — the API makes no outbound calls and accepts no service credentials.
