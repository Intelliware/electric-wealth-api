---
title: "Cross-Cutting Concerns"
block: reverse-eng/api/internal/cross-cutting-concerns
part: A
source_repo: electric-wealth-api-spring
source_version: 0.1.0
source_commit: 8a61cdeafb4537b16221d7aa0e9300010abd3387
extracted: 2026-10-15
---

## 13. Cross-Cutting Concerns

### 13.1 Audit Logging

No audit trail. The only record of who did what is the request log line
(`METHOD URI STATUS Nms trace=…`, no user id) and row timestamps such as `created_at`, `sent_at`, `consented_at`,
`terms_accepted_at`.

### 13.2 Soft Delete

Not used as a general pattern. State flags that behave like soft deletes:

- `sessions.revoked = 1` (logout / rotation).
- `accounts.hidden = 1` hides an account from lists, the dashboard total and `held` flags, but it stays addressable by id.
- `appointments.status = 'cancelled'`.

The only hard delete is `DELETE FROM watchlist` (and `registration_security_answers` replaced on re-submit).

### 13.3 Multi-Tenancy (if applicable)

Per-user data isolation only: every user-scoped query filters on `user_id` from the authenticated principal.
Reference data (securities, news, education content, glossary, security questions) is shared by all users.

### 13.4 Pagination

Offset pagination on four list endpoints: `GET /api/wealth/accounts`, `GET /api/wealth/accounts/{id}/transactions`,
`GET /api/wealth/goals`, `GET /api/wealth/documents`.

| Element | Implementation |
|---|---|
| Parameters | `page` (default 1, `@Min(1)`), `pageSize` (default 20, `@Min(1) @Max(100)`) |
| SQL | `LIMIT ? OFFSET ?`, offset from `SqlValues.offset(page, pageSize)` = `(page - 1) * pageSize` |
| Total | Separate `SELECT COUNT(*)` with the same `WHERE` |
| Return type | `PageResult<T>(List<T> items, long total)` → response `{ items, page, pageSize, total }` |

All other list endpoints return a bare JSON array (or an object wrapping one) without paging.

### 13.5 Sorting and Filtering

No client-controlled sorting; every list has a fixed `ORDER BY` in its repository. Filters:

| Endpoint | Filters |
|---|---|
| `/api/wealth/accounts/{id}/transactions` | `type` (`TransactionType`) |
| `/api/wealth/documents` | `year` (int), `category` (exact string) |
| `/api/education/content` | `level`, `type` |
| `/api/education/glossary` | `q` (≤60, contains, term or definition) |
| `/api/market/quote` | `symbols` (comma-separated) |
| `/api/market/search` | `q` (≤40, contains, symbol or name) |
| `/api/market/history` | `symbol` (required), `range` |

Dynamic `WHERE` clauses are assembled from fixed fragments with named parameters (`DocumentRepository.list`,
`EducationRepository.listContent`, `AccountRepository.listPostedTransactions`); user input never becomes SQL text.
`LIKE` input is escaped by `SqlValues.containsPattern` (`\ % _`) with `ESCAPE '\'`.

**Localisation** is another cross-cutting concern: the `Accept-Language` header (`fr…` → `fr-CA`, else `en-CA`)
selects `_fr`/`_en` columns (education, glossary, security questions) and French quick-link labels (`web/Labels`).
