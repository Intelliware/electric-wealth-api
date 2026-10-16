---
title: "Verification Report — electric-wealth-api-spring"
skill: reverse-engineer-api
step: verify (automated mode)
source_repo: electric-wealth-api-spring
source_version: 0.1.0
source_commit: 8a61cdeafb4537b16221d7aa0e9300010abd3387
extracted: 2026-10-15
---

## Verification Report — Electric Wealth API (Spring)

**Date:** 2026-10-15
**Mode:** automated
**Scope:** Full (Part A, Part B1, Part B2)
**Source commit:** `8a61cdeafb4537b16221d7aa0e9300010abd3387`

### Verdict

**MECHANICAL: GO**

Scope of this verdict: Tier M checks only. It attests that the extraction is well-formed — inventory reconciled,
no unresolved markers, cross-references resolve, declared source constructs match. It does NOT attest that the
documentation is correct. See UNVERIFIED below for what nobody has checked.

### Tier M — checked (18 passed / 0 failed)

Checks 1–16 come from one script run (`node re-close-out-check.mjs`, kept in the session workspace, not in this repo). Check 17 is the Spectral lint. Check 18 is the repo's own test suite, run on 2026-10-16 with JDK 21.

| # | Check | Result | Evidence |
|---|---|---|---|
| 1 | B1 `openapi.yaml` written with every path | PASS | paths in `openapi.json` = 41; path keys in `openapi.yaml` = 41 |
| 2 | Every controller handler has a spec operation and vice versa | PASS | `@*Mapping` handlers = 49; spec operations = 49; only-in-code = []; only-in-spec = [] |
| 3 | `extraction-status.md` inventory = spec operations | PASS | inventory rows = 49; missing = []; extra = [] |
| 4 | Status tracker shows every endpoint complete | PASS | rows not ✅ = 0 |
| 5 | Every operation in B2 `04-endpoint-catalog` | PASS | catalog rows = 49; missing = [] |
| 6 | Every operation in B2 `05-endpoint-detail-template` | PASS | detail entries = 49; missing = [] |
| 7 | Part A: 26 included blocks present (>1 KB), 5 gated blocks absent, no stray files | PASS | missing = []; gated present = []; extra = [] |
| 8 | Part B2: 13 included blocks present (>1 KB), 4 gated blocks absent, no stray files | PASS | missing = []; gated present = []; extra = [] |
| 9 | Side artifacts present | PASS | `extraction-status.md`, `CHANGE-TRACKER.md`, `openapi.yaml` |
| 10 | No TODO/TBD/FIXME/UNVERIFIED/`{{…}}`/HTML-comment markers | PASS | hits = 0 across 41 files |
| 11 | Relative links resolve | PASS | 70 links checked; broken = [] |
| 12 | Versions match `pom.xml` and spec | PASS | pom: project 0.1.0, Boot 4.1.1, Java 21, springdoc 3.1.1, sqlite-jdbc 3.53.4.0 = `01-api-overview.md`; spec `info.version` 0.1.0 |
| 13 | Error codes in source = codes in A/10 and B2/11 | PASS | 16 codes: ALREADY_ENROLLED, EMAIL_TAKEN, IDENTITY_MISMATCH, INTERNAL_ERROR, INVALID_CREDENTIALS, INVALID_MFA, INVALID_REFRESH, NOT_FOUND, NOT_SCHEDULED, NO_ADVISOR, RATE_LIMITED, REGISTRATION_NOT_FOUND, SLOT_UNAVAILABLE, STEP_OUT_OF_ORDER, UNAUTHORIZED, VALIDATION_ERROR; none missing/extra |
| 14 | Test counts in A/22 match `@Test` annotations | PASS | Auth 11, Wealth 18, Profile 14, Advisor 7, EducationAndMarket 11, Registration 4, OpenApiSpec 1, ContractParity 1 = 67 |
| 15 | Source unchanged since extraction; every doc stamped with the commit | PASS | `git rev-parse HEAD` = `8a61cde…`; `git status -- src pom.xml openapi.json` empty; unstamped docs = [] |
| 16 | Change tracker rows carry source version and verification status | PASS | 3 rows (before this verification entry) |
| 17 | OpenAPI spec passes a linter without errors | PASS | `npx @stoplight/spectral-cli@6 lint openapi.yaml` (ruleset `spectral:oas`) → `✖ 97 problems (0 errors, 97 warnings, 0 infos, 0 hints)` |
| 18 | Committed `openapi.json` matches the code; test suite passes | PASS | `JAVA_HOME=/opt/homebrew/Cellar/openjdk@21/21.0.12.1/… ./mvnw test` → exit 0. Surefire: 67 run, 0 failures, 0 errors, 1 skipped (`ContractParityTest`). `OpenApiSpecTest`: 1 run, 0 failures |

Presence of the companion-guide sections the API checklist requires — Getting Started (`01-quickstart`), Auth
(`02-authentication`), Pagination (`08-pagination`), Error Handling (`11-error-handling`), Rate limits
(`10-rate-limits`) — is covered by check 8.

### UNVERIFIED — Tier J, not attested (11 items)

| # | Check | Why it needs judgment | What I observed |
|---|---|---|---|
| 1 | `ContractParityTest` (parity with the Node API) | Skipped by its own assumption: `../electric-wealth-api` is not cloned on this machine | Not run; nothing attests parity with the Node API spec |
| 2 | Every documented behaviour traces to a source line | Deciding that a prose claim matches the code is interpretive | Claims were written while reading each file; source files are cited inline, but not every sentence is |
| 3 | Defaults match code | Choosing which construct is "the default" is interpretive | Spot-read: page 1 / pageSize 20 / max 100, TTL 900 s, rate 300/min, range `1Y`, share 7 days, registration 30 min |
| 4 | Parameter and request-body schemas match DTOs | Requires reading annotations against prose | Written from the DTO source; B1 is a 1:1 copy of the generated spec (which check 18 confirms is current) |
| 5 | Response shapes match response DTOs (not rows) | Which record is the response DTO is a judgment | B2 shapes were taken from the `*Dtos` records returned by the controllers |
| 6 | Error codes per endpoint traced to throws | The set of codes is checked (check 13); which endpoint raises which is interpretive | Per-endpoint lists in B2/05 were taken from controller code; the spec's `@ApiErrors` lists differ in one place (DELETE watchlist declares 404 but never throws it; documented as a gotcha) |
| 7 | Auth requirements match the security config | Path-prefix matching vs `@SecurityRequirement` was compared by reading | All 🔒 endpoints sit under `SecurityConfig.PROTECTED` prefixes; all public ones do not |
| 8 | No contradictions between Part A and Part B | Cross-document semantic comparison | None found while writing |
| 9 | No internal details leaked into Part B | "Internal" is a judgment call | B2 names two server settings (`ew.rate-limit-per-minute`, `PORT`/`DB_PATH`/`CORS_ORIGINS`) and the shared session store; judged useful to consumers, kept |
| 10 | Terminology consistent; Parts Affected values correct | Interpretive | Uses one vocabulary (A / B1 / B2 / All) |
| 11 | Spot-check against source | Sample cannot attest the population | 5 of 49 endpoints re-compared end to end (token, book appointment, transactions, open account, DELETE watchlist): matched |

A GO with a non-empty UNVERIFIED list means: well-formed, not attested as correct.

### Failures

None.

### Recommendations

1. Clone `electric-wealth-api` next to this repo and re-run `./mvnw test` so `ContractParityTest` runs instead of skipping (clears UNVERIFIED item 1).
2. Optional spec hygiene in the source repo (would clear 97 Spectral warnings): declare global `tags`, add `info.contact`, add operation descriptions, and emit `expiresIn: 900` as an integer `const` rather than a string enum. Each change must keep `ContractParityTest` green.
3. Consider fixing the observed quirks listed in [27-anti-patterns.md](27-anti-patterns.md) § 27.11: performance `range` is ignored, and DELETE watchlist declares a 404 it never returns.
