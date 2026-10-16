---
title: "Migrations and Schema Management"
block: reverse-eng/api/internal/migrations-and-schema-management
part: A
source_repo: electric-wealth-api-spring
source_version: 0.1.0
source_commit: 8a61cdeafb4537b16221d7aa0e9300010abd3387
extracted: 2026-10-15
---

## 16. Migrations and Schema Management

### 16.1 Migration Tool

This repo has **no migration tool and no DDL**. The schema is owned by the sibling `electric-wealth-db` repo:
plain SQL files in `electric-wealth-db/migrations/` (`001_initial_schema.sql` … `007_registration.sql`) applied by
its Node scripts (`scripts/migrate.js`, `scripts/reset.js`). `DatabaseConfig` states: "The schema belongs to that
repo, so this app never creates or migrates tables." No Flyway/Liquibase dependency is in `pom.xml`, and
`spring.sql.init` is not configured.

### 16.2 Creating a Migration

1. Add the next numbered file in `electric-wealth-db/migrations/` (and seed data under `seed/` if needed).
2. Rebuild locally: `cd ../electric-wealth-db && npm run db:reset`.
3. Update the affected repository SQL and DTO records here.
4. Run `./mvnw test` — `ApiTestBase` rebuilds a temp database with the db repo's `reset.js`, so tests always run against the current migrations.
5. The Node API reads the same file; coordinate the change there too.

### 16.3 Migration File Conventions

Observed in `electric-wealth-db/migrations/`: zero-padded three-digit prefix, snake_case feature name, one file per
feature area (`005_advisor.sql`, `006_market.sql`). The API depends on these schema features:

- Partial unique index `ux_appointments_advisor_slot` (005) — drives the 409 `SLOT_UNAVAILABLE` race handling.
- Unique keys for `ON CONFLICT` clauses: `education_progress (user_id, content_id)`, `watchlist (user_id, symbol)`.
- Foreign keys enforced at runtime (`foreign_keys=true` in the JDBC URL).

### 16.4 Migration Rules

- Never add DDL, `schema.sql` or migration libraries to this repo.
- A column rename breaks `Rows`-based mapping silently (record component no longer filled → null/0); update records in the same change.
- Enum-valued columns must store the enum's JSON value; adding a value means updating `ApiEnums` here.
- Keep the schema compatible with the Node API, which shares the database file and `sessions` table.
