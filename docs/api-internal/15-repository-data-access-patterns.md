---
title: "Repository / Data Access Patterns"
block: reverse-eng/api/internal/repository-data-access-patterns
part: A
source_repo: electric-wealth-api-spring
source_version: 0.1.0
source_commit: 8a61cdeafb4537b16221d7aa0e9300010abd3387
extracted: 2026-10-15
---

## 15. Repository / Data Access Patterns

### 15.1 Base Repository (if applicable)

No base class. Shared helpers instead:

| Helper | Purpose |
|---|---|
| [`Rows`](../../src/main/java/com/electricwealth/api/repository/Rows.java) | Cached record row mappers (column name → component name, 0/1 → boolean, enum by JSON value) |
| [`SqlValues`](../../src/main/java/com/electricwealth/api/repository/SqlValues.java) | `isoTimestamp`/`isoNow` (millis), `isoSeconds`/`isoNowSeconds`, `toInt`, `containsPattern` (LIKE escaping), `blankToNull`, `offset` |
| [`PageResult<T>`](../../src/main/java/com/electricwealth/api/repository/PageResult.java) | `items` + `total` for paged queries |
| [`JsonEnums`](../../src/main/java/com/electricwealth/api/config/JsonEnums.java) | `value(enum)` for writing enum columns; `parse` for hand mapping |

### 15.2 Writing a Repository

```java
@Repository
public class GoalRepository {

    private final JdbcClient jdbc;

    public GoalRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public PageResult<Goal> list(String userId, int page, int pageSize) {
        long total = jdbc.sql("SELECT COUNT(*) FROM goals WHERE user_id = ?").param(userId).query(Long.class).single();
        List<Goal> items = jdbc.sql("SELECT * FROM goals WHERE user_id = ? ORDER BY sort_order, id LIMIT ? OFFSET ?")
                .params(userId, pageSize, offset(page, pageSize))
                .query(Rows.of(Goal.class)).list();
        return new PageResult<>(items, total);
    }
}
```

Conventions: `@Repository`, constructor-injected `JdbcClient`, first parameter `userId` for user data, return
`Optional` for single lookups, return DTO records directly when the columns match.

### 15.3 Query Patterns

| Pattern | Example |
|---|---|
| Owner-scoped lookup | `SELECT * FROM accounts WHERE user_id = ? AND id = ?` → `Optional` |
| Paged list + count | `AccountRepository.listVisible`, `listPostedTransactions`, `GoalRepository.list`, `DocumentRepository.list` |
| Optional filters | Fixed fragments appended to a `StringBuilder` `where`; all named params always bound (unused ones are harmless) |
| Computed flags | `EXISTS (…) AS held`, `EXISTS (…) AS watched` in `MarketRepository.QUOTE_SELECT` |
| Language column | `title_%s AS title` with `%s` from a fixed `"en"`/`"fr"` (`EducationRepository.suffix`) — never user input |
| Upsert | `INSERT … ON CONFLICT (user_id, content_id) DO UPDATE …` (education progress); `ON CONFLICT DO NOTHING` (watchlist) |
| Insert-select | `INSERT INTO user_security_answers … SELECT … FROM registration_security_answers` |
| Next sort order | `SELECT COALESCE(MAX(sort_order), 0) + 1 …` inside `@Transactional` |
| Constraint as concurrency guard | Partial unique index on scheduled slots; violation detected by message `UNIQUE constraint failed` in `AdvisorController.bookingSlot` |
| Empty-aggregate guard | `SELECT MAX(week_of)` returns one NULL row; handled in `latestRoundup` |

### 15.4 N+1 Query Prevention

Collections are fetched with one query per parent, never per row:

- Account detail: 1 query for the account + 1 for holdings.
- Performance: 1 for the header + 1 for all points (split into `account`/`benchmark` series in Java).
- Dashboard: 6 queries (snapshot, visible-balance sum, allocations, goals, alerts, quick links).
- Quotes use correlated `EXISTS` subqueries rather than per-row lookups.

Known redundant work: `AdvisorRepository.isSlotOpen` recomputes the full 5-day availability (one query) for each
check; `AccountController.update` reads the account twice.

### 15.5 Raw SQL Conventions (if applicable)

- SQL keywords upper-case; multi-line SQL in Java text blocks (`"""`).
- `SELECT *` is common when mapping to a record; `Rows` ignores extra columns.
- Always bind values; string formatting is used only for fixed fragments (language suffix, optional `WHERE` clauses, `IN (:symbols)` toggling).
- Blank optional strings are written as NULL via `SqlValues.blankToNull`.
- Timestamps are written by Java (`SqlValues`) or SQLite `strftime('%Y-%m-%dT%H:%M:%SZ','now')`; both yield second-precision UTC ISO strings.
