---
title: "Performance Patterns and Constraints"
block: reverse-eng/api/internal/performance-patterns-and-constraints
part: A
source_repo: electric-wealth-api-spring
source_version: 0.1.0
source_commit: 8a61cdeafb4537b16221d7aa0e9300010abd3387
extracted: 2026-10-15
---

## 24. Performance Patterns and Constraints

### 24.1 Performance Budget (SLOs)

None defined in the repo. The request log records duration per request (`…ms`) for ad-hoc checks.

### 24.2 Pagination

Offset pagination, `pageSize` 1–100, default 20, on accounts, transactions, goals and documents. Every paged call
runs two queries (count + page). Scheduled transactions on the transactions endpoint are not paged. Other list
endpoints (notifications, appointments, messages, education content, glossary, quotes, news, baskets, watchlist,
security questions) return the full list.

### 24.3 N+1 Prevention

One query per collection, never per row; see [15-repository-data-access-patterns.md](15-repository-data-access-patterns.md) § 15.4.

### 24.4 Connection / Resource Pools

- HikariCP `maximum-pool-size: 4` against one SQLite file.
- SQLite allows one writer at a time; `busy_timeout=3000` makes a blocked statement wait up to 3 s before failing (surfacing as 500 `INTERNAL_ERROR`, except unique-index violations on appointments).
- Servlet threads: Boot defaults (no `server.tomcat.*` settings).

### 24.5 Payload Size Limits

No request size limit configured. Largest responses: the base64 PDF stub (small, one page) and unpaged lists.
Bean Validation caps individual fields (e.g. message text 2000, notes 500).

### 24.6 Caching for Performance

No HTTP or application caching. Only in-process memoisation of reflection work: `Rows.MAPPERS` (row mappers per
record type) and `JsonEnums.VALUES` (enum JSON values).

### 24.7 Heavy Operations — Move Off Request Path

None exist; all work is synchronous and short. `RegistrationRepository.complete` (~12 statements in one
transaction) is the largest write.

### 24.8 Known Performance Gotchas

- `RateLimitFilter.buckets` grows by one entry per distinct client IP and is never cleaned up.
- `AdvisorRepository.isSlotOpen` recomputes the full availability for each check.
- `EducationRepository.searchGlossary` loads all matching rows and sorts them in Java with a `Collator` (fine for the seed size).
- `MarketRepository.QUOTE_SELECT` evaluates two `EXISTS` subqueries per security row.
