---
title: "Database and ORM"
block: reverse-eng/api/internal/database-and-orm
part: A
source_repo: electric-wealth-api-spring
source_version: 0.1.0
source_commit: 8a61cdeafb4537b16221d7aa0e9300010abd3387
extracted: 2026-10-15
---

## 14. Database and ORM

### 14.1 Database Details

| Item | Value |
|---|---|
| Engine | SQLite (single file) |
| Driver | `org.xerial:sqlite-jdbc` 3.53.4.0 |
| File | `ew.db-path`, default `../electric-wealth-db/data/electric-wealth.db` |
| JDBC URL | `jdbc:sqlite:<abs path>?foreign_keys=true&busy_timeout=3000` (built in `DatabaseConfig`) |
| Pool | HikariCP, `maximum-pool-size: 4` |
| Schema owner | `electric-wealth-db` repo (`migrations/001…007`); this API never runs DDL |
| Shared with | The Node API (`electric-wealth-api`) — same file, same `sessions` table |

Tables used by this API (34), by migration file in `electric-wealth-db/migrations/`:

| Migration | Tables |
|---|---|
| `001_initial_schema.sql` | `users`, `user_profiles`, `user_preferences`, `sessions`, `accounts`, `holdings`, `transactions`, `account_performance`, `performance_points`, `documents`, `goals`, `portfolio_snapshots`, `asset_allocations`, `alerts`, `quick_links` |
| `002_notifications.sql` | `notifications` |
| `003_document_shares.sql` | `document_shares` |
| `004_education.sql` | `education_content`, `education_progress`, `glossary_terms` |
| `005_advisor.sql` | `advisors`, `appointments` (+ partial unique index `ux_appointments_advisor_slot` on `(advisor_id, date, time) WHERE status = 'scheduled'`), `advisor_messages` |
| `006_market.sql` | `securities`, `price_history`, `watchlist`, `market_news`, `market_roundup_items`, `baskets` |
| `007_registration.sql` | `offline_clients`, `security_questions`, `registrations`, `registration_security_answers`, `user_security_answers` |

### 14.2 Table Naming Conventions

- Plural snake_case table names (`accounts`, `advisor_messages`); join/child tables named `<parent>_<child>` (`document_shares`, `registration_security_answers`).
- Language variants as column suffixes: `title_en`/`title_fr`, `term_en`/`term_fr`, `definition_en`/`definition_fr`, `text_en`/`text_fr`.
- Many user-owned tables use a composite key of `user_id` + `id` (e.g. accounts are addressed `WHERE user_id = ? AND id = ?`; seed ids like `tfsa` are only unique per user).

### 14.3 Standard Column Patterns

| Pattern | Columns | Notes |
|---|---|---|
| Owner | `user_id` | On every user-scoped table; always in the `WHERE` |
| Ordering | `sort_order` | Append = `COALESCE(MAX(sort_order), 0) + 1` |
| Booleans | `hidden`, `read`, `mfa_verified`, `revoked`, `action_required`, `show_on_dashboard`, `notify_*`, `security_answers_saved` | INTEGER 0/1; `SqlValues.toInt` on write, converted by `Rows` on read |
| Money | `balance`, `amount`, `price`, `market_value`, `*_amount` | INTEGER cents |
| Timestamps | `created_at`, `expires_at`, `sent_at`, `added_at`, `updated_at`, `published_at`, `consented_at` | TEXT ISO-8601 UTC; `sessions` use millisecond precision (`isoTimestamp`), most others second precision (`isoSeconds` or `strftime('%Y-%m-%dT%H:%M:%SZ','now')`) |
| Dates | `date`, `target_date`, `date_of_birth` | TEXT `YYYY-MM-DD` |
| Enums | `type`, `status`, `category`, `language`, `meeting_type`, … | TEXT holding the enum's JSON value (`JsonEnums.value`) |

### 14.4 ORM Configuration and Conventions

No ORM. Data access uses Spring's fluent `JdbcClient` (auto-configured from the `DataSource`):

- Row mapping: `Rows.of(Record.class)` — a cached `DataClassRowMapper` with a `DefaultConversionService` extended by `JsonEnums.CONVERTER_FACTORY`, so `display_name` fills `displayName`, `0/1` become booleans and enum columns parse by JSON value. Hand-written `RowMapper` lambdas only where names do not line up (`MarketRepository.listBaskets`: `return_1y_pct`, `holdings_count`).
- Scalars: `.query(Long.class).single()`, `.query(Integer.class).optional()`.
- Parameters: positional `?` with `.params(...)`, or named `:name` with `.param("name", value)` when clauses are optional; `IN (:symbols)` takes a `List`.
- Writes: `.update()` returning the affected row count (used by `NotificationRepository.markRead/markAllRead`).
- Transactions: `@Transactional` on repository methods (see [11-service-layer-patterns.md](11-service-layer-patterns.md)).
