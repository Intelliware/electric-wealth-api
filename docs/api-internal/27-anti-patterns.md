---
title: "Anti-Patterns"
block: reverse-eng/api/internal/anti-patterns
part: A
source_repo: electric-wealth-api-spring
source_version: 0.1.0
source_commit: 8a61cdeafb4537b16221d7aa0e9300010abd3387
extracted: 2026-10-15
---

## 27. Anti-Patterns

Each entry is either a rule the code already follows (do not break it) or an existing defect/quirk observed in the
source (marked **Observed**).

#### 27.1 Anti-Pattern: Taking the user id from the request

**Wrong:** `@RequestParam String userId` or a user id in a body or path.
**Right:** `@AuthenticationPrincipal AuthenticatedUser user` → `user.userId()`, and include `user_id = ?` in every query.
**Why:** ownership is enforced only by the SQL filter (see [08-authentication-and-authorization-internals.md](08-authentication-and-authorization-internals.md)).

#### 27.2 Anti-Pattern: Throwing plain exceptions or returning `ResponseEntity` errors by hand

**Wrong:** `throw new IllegalArgumentException(...)` (→ 500) or `return ResponseEntity.status(400).body(...)`.
**Right:** `throw ApiException.validation("field: message")`, `ApiException.notFound(...)`, or `new ApiException(status, CODE, message)`, and list the status in `@ApiErrors`.

#### 27.3 Anti-Pattern: Mutating DTOs

Records are immutable; normalise input in the compact constructor (`Text.trim`) or in a derived accessor
(`AddressInput.normalizedPostalCode()`), not after binding.

#### 27.4 Anti-Pattern: N+1 Queries

Do not call a repository inside a `stream().map(...)` over rows. Fetch child rows for a parent in one query, or use
`EXISTS`/joins (`MarketRepository.QUOTE_SELECT`).

#### 27.5 Anti-Pattern: Logging PII

Do not log request bodies, tokens or emails. The only request logging is `TraceFilter`'s method/path/status line.

#### 27.6 Anti-Pattern: Swallowing exceptions

The single `catch` in controllers (`AdvisorController.bookingSlot`) re-throws anything that is not the expected
unique-constraint violation. Follow that shape.

#### 27.7 Anti-Pattern: Hardcoded Configuration

New settings go in `AppProperties` (`ew.*`) with a default in `application.yml`. **Observed:** several domain
constants are hardcoded in code — token TTL 900 s, registration TTL 30 min, share TTL 7 days, slot times, the
5-day booking window, TFSA/FHSA contribution room, the demo MFA code. Changing them needs a code change and may
break Node parity.

#### 27.8 Anti-Pattern: Catch-and-Continue Inside Transactions

Do not catch `DataAccessException` inside a `@Transactional` repository method and keep going; let it propagate so
the transaction rolls back (`RegistrationRepository.complete` relies on this).

#### 27.9 Anti-Pattern: Forgotten Pagination

New lists over user-growing tables should take `page`/`pageSize` with the standard bounds and return
`{items, page, pageSize, total}`. **Observed:** notifications, appointments and advisor messages are unpaged.

#### 27.10 Anti-Pattern: Untyped values at module boundaries

Use enums from `ApiEnums` for closed sets and `@Nullable` for optional fields. **Observed:** `DocumentController.list`
takes `category` as a free `String`, so the spec cannot list valid values.

#### 27.11 Observed quirks to keep in mind

| Quirk | Source | Effect |
|---|---|---|
| Performance `range` not applied | `AccountController.performance` passes `range` only to the response | All ranges return the same series |
| `DELETE /api/market/watchlist/{symbol}` declares 404 but never throws it | `MarketController.removeFromWatchlist` | Unknown/valid-format symbols return 200 with the unchanged list |
| `availability?meetingType=` ignored | `AdvisorController.availability` | Documented as "accepted for v1 compatibility" |
| MFA `challengeId`/`method` ignored | `AuthController.verifyMfa` | Any value accepted |
| Refresh tokens never expire on their own | `SessionRepository.rotate` | Valid until used or revoked |
| Goals use UTC "today", advisor uses Toronto "today" | `GoalController.create`, `AdvisorRepository.getAvailability` | Boundary differences around midnight |
| Rate-limit buckets never evicted | `RateLimitFilter` | Memory grows with distinct IPs |
