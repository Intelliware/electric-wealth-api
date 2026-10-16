---
title: "Appendix A: Complete Feature Implementation Walkthrough"
block: reverse-eng/api/internal/appendix-a-complete-feature-implementation-walkthrough
part: A
source_repo: electric-wealth-api-spring
source_version: 0.1.0
source_commit: 8a61cdeafb4537b16221d7aa0e9300010abd3387
extracted: 2026-10-15
---

## Appendix A: Complete Feature Implementation Walkthrough

The walkthrough traces an existing feature end to end — **advisor appointment booking**
(`POST /api/advisor/appointments`) — because it touches every layer, including a concurrency rule.

#### A.1 The Feature

A signed-in client books a meeting with their assigned advisor in an open slot. Errors: 400 invalid input,
401 not signed in, 404 `NO_ADVISOR`, 409 `SLOT_UNAVAILABLE` (slot not offered or just taken).

#### A.2 OpenAPI Change (Section 3)

Generated from annotations; the committed spec is regenerated with
`./mvnw test -Dtest=OpenApiSpecTest -Dopenapi.write=true`. Resulting operation:
`paths./api/advisor/appointments.post` with body `AppointmentCreateRequest`, `201 Appointment`, errors 400/401/404/409,
`security: bearerAuth`.

#### A.3 Migration (Section 16)

In `electric-wealth-db/migrations/005_advisor.sql`: tables `advisors`, `appointments`, `advisor_messages` and
`CREATE UNIQUE INDEX ux_appointments_advisor_slot ON appointments(advisor_id, date, time) WHERE status = 'scheduled';`

#### A.4 Domain Model & Rule (Section 12)

- Slots: `AdvisorRepository.SLOT_TIMES = 09:00, 10:30, 12:00, 14:30, 16:00`; window = next 5 weekdays after today in `America/Toronto`.
- A slot is open if no `scheduled` appointment of the advisor holds it and the advisor is `available`.

#### A.5 Repository (Section 15)

```java
public String createAppointment(String userId, String advisorId, AppointmentCreateRequest input) {
    String id = "appt-" + UUID.randomUUID().toString().substring(0, 8);
    jdbc.sql("""
            INSERT INTO appointments (id, user_id, advisor_id, meeting_type, date, time, status, notes, created_at)
            VALUES (?, ?, ?, ?, ?, ?, 'scheduled', ?, ?)""")
            .params(id, userId, advisorId, JsonEnums.value(input.meetingType()), input.date().toString(),
                    input.time(), SqlValues.blankToNull(input.notes()), SqlValues.isoNowSeconds())
            .update();
    return id;
}
```

#### A.6 DTO + Validation (Section 9)

```java
public record AppointmentCreateRequest(
        @NotNull MeetingType meetingType,
        @NotNull @Schema(example = "2027-06-30") LocalDate date,
        @NotNull @Pattern(regexp = SLOT_TIME_PATTERN, message = "Must be HH:MM") @Schema(example = "14:30") String time,
        @Size(max = 500) @Nullable String notes) {

    public AppointmentCreateRequest {
        notes = Text.trim(notes);
    }
}
```

#### A.7 Service (Section 11)

No service layer; the controller orchestrates. The race between "slot is open" and the insert is closed by the
partial unique index; `bookingSlot` converts the violation:

```java
private static <T> T bookingSlot(Supplier<T> change) {
    try {
        return change.get();
    } catch (DataAccessException e) {
        if (String.valueOf(e.getMostSpecificCause().getMessage()).contains("UNIQUE constraint failed")) {
            throw slotTaken();          // 409 SLOT_UNAVAILABLE
        }
        throw e;
    }
}
```

#### A.8 Controller (Section 7)

```java
@PostMapping("/appointments")
@ResponseStatus(HttpStatus.CREATED)
@Operation(summary = "Book a meeting in an open slot")
@ApiResponse(responseCode = "201", description = "Booked")
@ApiErrors({ 400, 401, 404, 409 })
public Appointment book(@AuthenticationPrincipal AuthenticatedUser user,
        @Valid @RequestBody AppointmentCreateRequest body) {
    var advisor = advisorOr404(user.userId());                       // 404 NO_ADVISOR
    if (!advisors.isSlotOpen(advisor, body.date(), body.time())) {
        throw slotTaken();                                           // 409 outside window / taken
    }
    String id = bookingSlot(() -> advisors.createAppointment(user.userId(), advisor.id(), body));
    return advisors.findAppointment(user.userId(), id).orElseThrow();
}
```

Request path: `TraceFilter` → `RateLimitFilter` → security (`/api/advisor/**` is protected) → validation →
handler → JSON 201.

#### A.9 Background Job — Send Invitation Email (Section 18)

Not applicable: no background jobs exist (section 18 is gated out). No confirmation email is sent.

#### A.10 Tests (Section 22)

`AdvisorApiTest.booksASlotHidesItFromEveryoneAndRefusesADoubleBooking` and
`rejectsSlotsOutsideTheBookingWindowOrSchedule` cover the success path, slot disappearance from availability and
the 409s.

#### A.11 Observability Verification (Section 21)

Each call produces `POST /api/advisor/appointments 201 <n>ms trace=trace-…`; a 409 body carries the same trace id.

#### A.12 Feature-Flag Gating (Section 26)

Not applicable: no feature flags (section 26 is gated out).

#### A.13 Checklist Walkthrough (Section 28)

Contract (DTO + enums) ✔ · controller with `@ApiErrors` ✔ · protected prefix ✔ · ownership (`user_id`) ✔ ·
DB-level concurrency guard ✔ · integration tests ✔ · spec regenerated ✔.
