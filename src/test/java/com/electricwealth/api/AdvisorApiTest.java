package com.electricwealth.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import com.electricwealth.api.repository.AdvisorRepository;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Ported from the "advisor" tests in test/app.test.ts. */
class AdvisorApiTest extends ApiTestBase {

    private List<String> availableDates(String token) {
        return read(get("/api/advisor/availability", token), "$.days[*].date");
    }

    private List<String> slots(String token, int day) {
        return read(get("/api/advisor/availability", token), "$.days[" + day + "].slots");
    }

    @Test
    void computesTheNextFiveBusinessDays() {
        // 2026-10-09 is a Friday.
        assertThat(AdvisorRepository.nextBusinessDays(LocalDate.parse("2026-10-09"), 5)).map(LocalDate::toString)
                .containsExactly("2026-10-12", "2026-10-13", "2026-10-14", "2026-10-15", "2026-10-16");
    }

    @Test
    void offersFiveDaysOfSlotsWithTheAssignedAdvisor() {
        MvcTestResult res = get("/api/advisor/availability", login().accessToken());
        assertThat(json(res, "$.advisor.name")).isEqualTo("Jordan Avery");
        assertThat(json(res, "$.advisor.designation")).isEqualTo("CFP");
        assertThat(this.<List<?>>read(res, "$.days")).hasSize(5);
        assertThat(this.<List<String>>read(res, "$.days[0].slots"))
                .containsExactly("09:00", "10:30", "12:00", "14:30", "16:00");
    }

    @Test
    void booksASlotHidesItFromEveryoneAndRefusesADoubleBooking() {
        String sarah = login().accessToken();
        String linda = login("linda.rodriguez@example.com").accessToken();
        String date = availableDates(sarah).get(1);
        MvcTestResult res = postJson("/api/advisor/appointments", sarah,
                "{\"meetingType\":\"phone\",\"date\":\"%s\",\"time\":\"10:30\",\"notes\":\"Year-end review\"}".formatted(date));
        assertThat(res).hasStatus(201);
        assertThat(json(res, "$.status")).isEqualTo("scheduled");
        assertThat(json(res, "$.meetingType")).isEqualTo("phone");
        assertThat(json(res, "$.advisorId")).isEqualTo("adv-1");

        assertThat(slots(linda, 1)).doesNotContain("10:30");
        MvcTestResult clash = postJson("/api/advisor/appointments", linda,
                "{\"meetingType\":\"video\",\"date\":\"%s\",\"time\":\"10:30\"}".formatted(date));
        assertThat(clash).hasStatus(409);
        assertThat(json(clash, "$.error.code")).isEqualTo("SLOT_UNAVAILABLE");
    }

    @Test
    void rejectsSlotsOutsideTheBookingWindowOrSchedule() {
        String token = login("david.kim@example.com").accessToken();
        String firstDay = availableDates(token).getFirst();
        for (String body : List.of(
                "{\"meetingType\":\"video\",\"date\":\"2024-12-09\",\"time\":\"09:00\"}",
                "{\"meetingType\":\"video\",\"date\":\"%s\",\"time\":\"08:00\"}".formatted(firstDay))) {
            assertThat(postJson("/api/advisor/appointments", token, body)).as(body).hasStatus(409);
        }
        assertThat(postJson("/api/advisor/appointments", token,
                "{\"meetingType\":\"video\",\"date\":\"%s\",\"time\":\"9am\"}".formatted(firstDay))).hasStatus(400);
    }

    @Test
    void reschedulesFreesTheOldSlotAndCancels() {
        String token = login("michael.thompson@example.com").accessToken();
        List<String> days = availableDates(token);
        String id = json(postJson("/api/advisor/appointments", token,
                "{\"meetingType\":\"video\",\"date\":\"%s\",\"time\":\"09:00\"}".formatted(days.get(2))), "$.id");

        MvcTestResult moved = patch("/api/advisor/appointments/" + id, token,
                "{\"date\":\"%s\",\"time\":\"16:00\"}".formatted(days.get(3)));
        assertThat(json(moved, "$.date")).isEqualTo(days.get(3));
        assertThat(json(moved, "$.time")).isEqualTo("16:00");
        assertThat(json(moved, "$.status")).isEqualTo("scheduled");
        assertThat(slots(token, 2)).contains("09:00");
        assertThat(slots(token, 3)).doesNotContain("16:00");

        MvcTestResult cancelled = patch("/api/advisor/appointments/" + id, token, "{\"status\":\"cancelled\"}");
        assertThat(json(cancelled, "$.status")).isEqualTo("cancelled");
        assertThat(patch("/api/advisor/appointments/" + id, token, "{\"status\":\"cancelled\"}")).hasStatus(409);
        assertThat(patch("/api/advisor/appointments/" + id, token, "{\"date\":\"%s\"}".formatted(days.get(3))))
                .hasStatus(400);
    }

    @Test
    void listsTheUsersAppointmentsIncludingPastOnes() {
        MvcTestResult res = get("/api/advisor/appointments", login().accessToken());
        assertThat(json(res, "$[0].id")).isEqualTo("appt-sarah-1");
        assertThat(json(res, "$[0].status")).isEqualTo("completed");
    }

    @Test
    void keepsAMessageThreadAndAppendsClientMessages() {
        String token = login("emma.wilson@example.com").accessToken();
        assertThat(this.<List<?>>read(get("/api/advisor/messages", token), "$")).hasSize(1);
        MvcTestResult sent = postJson("/api/advisor/messages", token, "{\"text\":\"  Thanks Jordan!  \"}");
        assertThat(sent).hasStatus(201);
        assertThat(json(sent, "$.from")).isEqualTo("client");
        assertThat(json(sent, "$.text")).isEqualTo("Thanks Jordan!");
        List<String> ids = read(get("/api/advisor/messages", token), "$[*].id");
        assertThat(ids.getLast()).isEqualTo(json(sent, "$.id"));
        assertThat(postJson("/api/advisor/messages", token, "{\"text\":\"   \"}")).hasStatus(400);
    }
}
