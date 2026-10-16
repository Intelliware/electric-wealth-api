---
title: "Service Layer Patterns"
block: reverse-eng/api/internal/service-layer-patterns
part: A
source_repo: electric-wealth-api-spring
source_version: 0.1.0
source_commit: 8a61cdeafb4537b16221d7aa0e9300010abd3387
extracted: 2026-10-15
---

## 11. Service Layer Patterns

### 11.1 Service Structure

There is **no service layer** in this codebase: no `@Service` classes exist in `src/main`. The responsibilities a
service would normally hold are split:

| Responsibility | Where it lives | Example |
|---|---|---|
| Orchestration of several repository calls | Controller | `AccountController.update`: `findOr404` → `update` → `findOr404` → `listHoldings` |
| Business rules needing request context | Controller | `GoalController.create` date / amount checks; `AdvisorController.update` state rules |
| Business defaults on write | Repository | `AccountRepository.NewAccountRules`; `RegistrationRepository.complete` seeds preferences and quick links |
| Aggregation across tables | Repository | `DashboardRepository.find` (5 tables + `AccountRepository.totalVisibleBalance`) |

When a feature needs logic shared by several controllers, the existing precedent is to put it in a repository
(e.g. `SessionRepository` is used by `AuthController`, `RegistrationController` and `BearerTokenFilter`).

### 11.2 Transaction Management

- Spring's declarative `@Transactional` (from `org.springframework.transaction.annotation`) on **repository methods** only; backed by the JDBC `DataSourceTransactionManager` auto-configured over the Hikari pool.
- Methods without `@Transactional` run each statement in auto-commit mode.

| Method | Why it is transactional |
|---|---|
| `SessionRepository.rotate` | Revoke old session + insert new one |
| `AccountRepository.open` | `MAX(sort_order)+1`, insert account, insert empty `account_performance` row |
| `GoalRepository.create` | `MAX(sort_order)+1` + insert |
| `ProfileRepository.updatePreferences` | Read current preferences, merge partial update, write |
| `RegistrationRepository.saveSecurityAnswers` | Delete + insert answers + flag registration |
| `RegistrationRepository.complete` | Creates user, profile, preferences, security answers, portfolio snapshot, welcome notification, 3 quick links; marks offline client enrolled and registration complete — "a failure leaves nothing half-created" |

Not transactional but multi-step (check-then-act): `AdvisorController.book`/`update` (slot check then insert/update —
protected by the partial unique index `ux_appointments_advisor_slot` and the `bookingSlot` wrapper),
`RegistrationController.complete` (email-in-use check before the transactional `complete`),
`AccountController.update` (two separate `UPDATE`s for nickname and hidden).

### 11.3 Service-to-Service Calls

Not applicable — single process, no outbound calls. In-process collaboration is constructor-injected repositories.

### 11.4 Method Naming Conventions

Repository methods (the de facto service API):

| Prefix | Returns | Example |
|---|---|---|
| `find…` | `Optional<T>` | `find(userId, id)`, `findActive(token)`, `findUserAdvisor(userId)` |
| `list…` | `List<T>` or `PageResult<T>` | `listVisible`, `listPostedTransactions`, `listQuotes` |
| `create…` / `open` / `start` / `add…` | new id or created record | `createAppointment` → id; `GoalRepository.create` → `Goal`; `addClientMessage` → `AdvisorMessage` |
| `update…` / `mark…` / `save…` | `void`, `boolean`, `int`, or `Optional` | `markRead` → `boolean` (row found); `markAllRead` → `int` (rows changed) |
| `is…` / `…Exists` / `…InUse` | `boolean` | `isSlotOpen`, `contentExists`, `emailInUse` |
| `remove…` / `revoke` / `cancel…` | `void` | `removeFromWatchlist`, `cancelAppointment` |

Controller methods are named after the action (`list`, `detail`, `open`, `update`, `book`, `share`, `markRead`).
