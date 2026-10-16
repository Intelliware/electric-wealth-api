---
title: "Testing"
block: reverse-eng/api/internal/testing
part: A
source_repo: electric-wealth-api-spring
source_version: 0.1.0
source_commit: 8a61cdeafb4537b16221d7aa0e9300010abd3387
extracted: 2026-10-15
---

## 22. Testing

### 22.1 Test Pyramid

All tests are **integration tests** through the full Spring context and filter chain (MockMvc), against a real
SQLite database. There are no unit tests and no mocks.

| Class | Tests | Covers |
|---|---|---|
| `AuthApiTest` | 11 | Health, login + MFA, invalid credentials/MFA, unverified token, 401 envelope, refresh rotation, logout, validation messages, unknown enum, unknown route |
| `WealthApiTest` | 18 | Dashboard (all six personas, French labels), accounts (visible only, detail, 404, transactions paging/filter, open rules, nickname patch/clear), documents (list/filter, PDF, share) |
| `ProfileApiTest` | 14 | Profile, address update/normalisation, preferences merge, goals list/create/validation, notifications |
| `AdvisorApiTest` | 7 | Business-day calculation, availability, booking + double-booking, window rules, reschedule/cancel, appointments, messages |
| `EducationAndMarketApiTest` | 11 | Catalogue/progress/glossary, quotes held/watched, security detail, history, search, watchlist, news/roundup/baskets |
| `RegistrationApiTest` | 4 | End-to-end enrolment and sign-in, French client, rejections, step order and consents |
| `OpenApiSpecTest` | 1 | Committed `openapi.json` equals the generated spec |
| `ContractParityTest` | 1 | Spec matches `../electric-wealth-api/openapi.json` (skipped if absent) |

The feature tests are ports of the Node API's `test/app.test.ts` (class Javadocs).

### 22.2 Running Tests

```bash
./mvnw test                                                  # all tests (needs JDK 21 and node on PATH)
./mvnw test -Dtest=WealthApiTest                             # one class
./mvnw test -Dtest=OpenApiSpecTest -Dopenapi.write=true      # regenerate openapi.json
```

Requirements: JDK 21 (running on an older JVM fails with "class file version 65.0"; Homebrew's `openjdk@21` is
keg-only, so set `JAVA_HOME` to it if `java -version` shows another version), Node.js on `PATH`, and the
`electric-wealth-db` repo cloned next to this one.

### 22.3 Unit Test Skeleton

No unit tests exist. A pure static helper can be tested directly, as `AdvisorApiTest.computesTheNextFiveBusinessDays`
does with `AdvisorRepository.nextBusinessDays` (it still extends `ApiTestBase`).

### 22.4 Integration Test Skeleton

```java
class GoalsApiTest extends ApiTestBase {

    @Test
    void createsAGoal() {
        String token = login().accessToken();                     // password + MFA as sarah.chen@example.com
        MvcTestResult res = postJson("/api/wealth/goals", token, """
                {"name":"Trip","category":"Vacation","targetAmount":500000,"currency":"CAD","targetDate":"2099-01-01"}""");
        assertThat(res).hasStatus(201);
        assertThat(json(res, "$.progressPct")).isEqualTo("0");
        // Check persistence directly:
        assertThat(jdbc.sql("SELECT COUNT(*) FROM goals WHERE name = 'Trip'").query(Long.class).single()).isEqualTo(1);
    }
}
```

### 22.5 Fixtures and Factories

- [`ApiTestBase`](../../src/test/java/com/electricwealth/api/ApiTestBase.java): `@SpringBootTest(properties = {"ew.log-requests=false", "ew.rate-limit-per-minute=100000"})`, `@AutoConfigureMockMvc`.
- Database: built **once per JVM** into a temp dir by running `node ../electric-wealth-db/scripts/reset.js --db <tmp>/test.db`; injected through `@DynamicPropertySource` as `ew.db-path`. The database is shared by all tests in the run, so tests must not depend on each other's writes (use different personas or assert relative changes).
- Helpers: `login(email)` (password + MFA), `passwordLogin`, `get/put/patch/delete/postJson` with bearer token, `json(result, "$.path")`, `read`, `number`, `PASSWORD = "Demo123!"`, injected `jdbc` for DB assertions.
- Personas come from the db repo's seed files (e.g. `sarah.chen@example.com`, `emma.wilson@example.com`).

### 22.6 Mocking External Services

Not applicable — there are no external services.

### 22.7 What MUST be Tested

- Every new endpoint: success shape, each documented error status/code, and 401 without a token if protected.
- Persistence of writes (query `jdbc` directly).
- Regenerate and commit `openapi.json`; `OpenApiSpecTest` must pass.
- `ContractParityTest` must pass when the Node API is cloned alongside.

### 22.8 What MUST NOT Be in Tests

- Writes to the developer's real database — always go through `ApiTestBase` (temp copy).
- Ordering assumptions between test methods or classes.
- Real rate limiting (the base raises the limit to 100 000/min).
