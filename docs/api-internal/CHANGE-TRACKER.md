---
title: "Change Tracker — electric-wealth-api-spring documentation"
skill: reverse-engineer-api
source_repo: electric-wealth-api-spring
---

# Change Tracker — electric-wealth-api-spring documentation

### Header

| Field | Value |
|---|---|
| Artifact | Electric Wealth API (Spring) — reverse-engineered documentation |
| Artifact type | RE package (Part A internal + Part B1 OpenAPI + Part B2 consumer guide) |
| Current source version | 0.1.0 @ `8a61cdeafb4537b16221d7aa0e9300010abd3387` (branch `kb1`) |
| Current artifact version | 0.1.0-doc.1 |
| Stage / Skill of record | reverse-engineering / `reverse-engineer-api` |
| last_verified_against_commit | `8a61cdeafb4537b16221d7aa0e9300010abd3387` |

### Versioning Convention

`X.Y.Z-doc.N` — `X.Y.Z` is the API's `pom.xml` version; `N` increments for each documentation revision against
that source version.

### Parts Affected — Reference

| Value | Meaning |
|---|---|
| A | Part A — internal docs in `docs/api-internal/` (this repo) |
| B1 | Part B1 — `<KB>/source-docs/electric-wealth-api-spring/openapi.yaml` |
| B2 | Part B2 — `<KB>/source-docs/electric-wealth-api-spring/api-consumer/` |
| All | A, B1 and B2 |

KB root: `electric-wealth-knowledge-base`.

### Change Types

Initial Extraction · Correction · Source Update · Restructure · Addition · Deprecation · Removal · Verified.

### Change Log

#### 0.1.0-doc.1 — 2026-10-15

| # | Date | Change Type | Parts Affected | Section / File | Description | Source Version | Verified |
|---|---|---|---|---|---|---|---|
| 5 | 2026-10-16 | Correction | A | [verification-report.md](verification-report.md), 22-testing | Ran `./mvnw test` with JDK 21: 66 passed, 1 skipped (`ContractParityTest`), 0 failed; `OpenApiSpecTest` passed. Moved the spec-currency check from UNVERIFIED to Tier M. Corrected the earlier claim that JDK 21 was unavailable. | 0.1.0 @ 8a61cde | automated (mechanical GO; 18 Tier M pass; 11 UNVERIFIED) |
| 4 | 2026-10-15 | Verified | All | [verification-report.md](verification-report.md) | RE close-out gate, automated mode: 17 Tier M checks passed, 0 failed; Spectral 0 errors / 97 warnings; `./mvnw test` not run (JDK 21 not located — corrected in #5). | 0.1.0 @ 8a61cde | automated (mechanical GO; 12 UNVERIFIED) |
| 3 | 2026-10-15 | Initial Extraction | B1 | `openapi.yaml` | Converted the committed `openapi.json` (springdoc output, OpenAPI 3.1.0, 49 operations) to YAML. | 0.1.0 @ 8a61cde | 🔵 see verification row |
| 2 | 2026-10-15 | Initial Extraction | B2 | `api-consumer/01…15` (13 files) | Consumer guide: quickstart, auth, environments, catalog, endpoint details, request/response format, pagination, filtering, rate limits, errors, versioning, changelog. | 0.1.0 @ 8a61cde | 🔵 see verification row |
| 1 | 2026-10-15 | Initial Extraction | A | `01…31` (26 files), `extraction-status.md` | Full Part A extraction from source; 5 blocks gated out (caching, background jobs, inter-service, external integrations, feature flags). | 0.1.0 @ 8a61cde | 🔵 see verification row |

### Detailed Changes

#### Change #1–3 — initial extraction

| Field | Value |
|---|---|
| Date | 2026-10-15 |
| Change Type | Initial Extraction |
| Parts Affected | All |
| Section / File | All files |
| Source Version | 0.1.0 @ `8a61cdeafb4537b16221d7aa0e9300010abd3387` |

**Description:** First reverse-engineering pass over every file in `src/main` and `src/test`, `pom.xml`,
`application.yml`, `.env.example`, `README.md` and `openapi.json`, plus the `electric-wealth-db` migrations for
table names and the appointment slot index.

**Endpoint reconciliation:** 49 handler methods in `web/*Controller.java` = 49 operations in `openapi.json` =
"49 endpoints" in `README.md`.

**Verification:** first run on 2026-10-15 used the default `java` (JDK 17) and failed with "class file version
65.0"; the Homebrew JDK 21 (`/opt/homebrew/Cellar/openjdk@21/21.0.12.1`, not registered with `java_home`) was
missed. Re-run on 2026-10-16 with that JDK: 67 tests, 0 failures, 1 skipped (`ContractParityTest`, Node API not
cloned). `OpenApiSpecTest` passed, so the committed `openapi.json` (and therefore B1) matches the code.

**Cross-Impact:** Part B1 is derived from `openapi.json`; any regeneration of that file requires re-converting B1
and re-checking B2 endpoint pages (04, 05).

### Cross-Part Synchronisation

| If this changes… | Check… |
|---|---|
| A controller method, path, status or `@ApiErrors` | `openapi.json` (regenerate) → B1 `openapi.yaml`; B2 04-endpoint-catalog, 05-endpoint-detail-template, 11-error-handling; A 03, 10 |
| A DTO record or enum | B1 schemas; B2 05, 06, 07; A 09, 12, 29 |
| `SecurityConfig` / `BearerTokenFilter` / `SessionRepository` | B2 02-authentication; A 07, 08, 23 |
| `RateLimitFilter` or `ew.rate-limit-per-minute` | B2 10-rate-limits; A 07, 23 |
| `GlobalExceptionHandler` / `ApiException` codes | B2 07, 11; A 10 |
| Paging params or `PageResult` | B2 08; A 13, 24 |
| `application.yml` / `.env.example` | B2 03; A 02, 25 |
| `pom.xml` version | Header of this file; B2 14, 15 |
