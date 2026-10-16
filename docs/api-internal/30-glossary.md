---
title: "Glossary"
block: reverse-eng/api/internal/glossary
part: A
source_repo: electric-wealth-api-spring
source_version: 0.1.0
source_commit: 8a61cdeafb4537b16221d7aa0e9300010abd3387
extracted: 2026-10-15
---

## 30. Glossary

#### 30.1 Domain Terms

| Term | Meaning in this codebase |
|---|---|
| Persona | A seeded demo user (e.g. `sarah.chen@example.com`); password `Demo123!` |
| Visible account | `accounts.hidden = 0`; counted in lists, dashboard total and `held` |
| Contribution room | Remaining registered-plan room in cents; `null` when not applicable |
| Posted / scheduled transaction | `transactions.status` `posted` (paged history) vs `scheduled` (upcoming, always returned) |
| Time-weighted return | `account_performance.time_weighted_return_pct` |
| Quick link | Dashboard shortcut (`label`, `deepLink` `electricwealth://…`) |
| Offline client / branch record | Existing client in `offline_clients`, matched during online enrolment |
| Enrolment / registration | The 5-step `/api/registration` flow creating online access |
| Slot | An advisor meeting start time (`HH:MM`, Toronto) on a business day |
| Watchlist | Securities a user follows (`watchlist` table) |
| Basket | Thematic group of securities (`baskets`) |
| Roundup | Weekly market summary (`market_roundup_items.week_of`) |

#### 30.2 Acronyms

| Acronym | Meaning |
|---|---|
| TFSA / RRSP / RESP / RDSP / FHSA / LIRA / RRIF | Canadian account types in `AccountType` |
| MFA | Multi-factor authentication (demo code `123456`) |
| SIN | Social Insurance Number (masked in profile) |
| TSX | Toronto Stock Exchange (benchmark "S&P/TSX Composite") |
| MDC | SLF4J Mapped Diagnostic Context (`traceId`) |

#### 30.3 ID Prefixes

| Prefix | Entity | Created by |
|---|---|---|
| `mock-at-` / `mock-rt-` | Access / refresh token | `SessionRepository.create` |
| `trace-` | Request trace id | `TraceIds.newId` |
| `u-` | User | `RegistrationRepository.complete` |
| `reg-` | Registration | `RegistrationRepository.start` |
| `acct-` | Account opened online | `AccountRepository.open` |
| `goal-` | Goal | `GoalRepository.create` |
| `appt-` | Appointment | `AdvisorRepository.createAppointment` |
| `msg-` | Advisor message | `AdvisorRepository.addClientMessage` |
| `welcome` | Welcome notification id | `RegistrationRepository.complete` |

Seeded ids (`tfsa`, `d1`, `no1`, `e1`) come from the db repo's seed files.

#### 30.4 Codebase Shorthand

| Term | Meaning |
|---|---|
| Node API | The original TypeScript API `electric-wealth-api` this repo ports |
| Contract parity | Spec equality with the Node API, checked by `ContractParityTest` |
| Envelope | `ErrorEnvelope` error body |
| JSON value (of an enum) | The `@JsonProperty` string, used in JSON, query params and DB |
| `…Or404` | Controller helper that loads or throws 404 |

#### 30.5 Deprecated Terms (Avoid in New Code)

None marked in the source. `meetingType` on `GET /api/advisor/availability` is described as "Accepted for v1
compatibility; every type has the same slots".
