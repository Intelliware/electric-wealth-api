package com.electricwealth.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Ported from the "profile", "preferences", "goals" and "notifications" tests in test/app.test.ts. */
class ProfileApiTest extends ApiTestBase {

    // --- profile ---

    @Test
    void returnsTheProfileWithTrustedContact() {
        MvcTestResult res = get("/api/profile", login().accessToken());
        assertThat(json(res, "$.name")).isEqualTo("Sarah Chen");
        assertThat(res).bodyJson().extractingPath("$.trustedContact")
                .isEqualTo(Map.of("name", "David Chen", "relationship", "Sibling"));
    }

    @Test
    void updatesTheAddressAndNormalizesThePostalCode() {
        String token = login("emma.wilson@example.com").accessToken();
        MvcTestResult res = put("/api/profile/address", token, """
                {"address": {"line1": " 1 Harbour Lane ", "line2": "Unit 4", "city": "Dartmouth", "province": "NS",
                             "postalCode": "b2y4a1", "country": "Canada"}}""");
        assertThat(res).hasStatusOk();
        assertThat(res).bodyJson().extractingPath("$.address").isEqualTo(Map.of(
                "line1", "1 Harbour Lane",
                "line2", "Unit 4",
                "city", "Dartmouth",
                "province", "NS",
                "postalCode", "B2Y 4A1",
                "country", "Canada"));
        assertThat(json(get("/api/profile", token), "$.address.city")).isEqualTo("Dartmouth");
    }

    @Test
    void clearsAddressLine2WhenItIsBlank() {
        String token = login("emma.wilson@example.com").accessToken();
        MvcTestResult res = put("/api/profile/address", token, """
                {"address": {"line1": "1 Harbour Lane", "line2": "  ", "city": "Dartmouth", "province": "NS",
                             "postalCode": "B2Y 4A1", "country": "Canada"}}""");
        assertThat(res).bodyJson().doesNotHavePath("$.address.line2");
    }

    @Test
    void rejectsAnInvalidPostalCodeOrProvince() {
        String token = login().accessToken();
        for (String address : List.of(
                "{\"line1\":\"1 Main St\",\"city\":\"Toronto\",\"country\":\"Canada\",\"province\":\"ON\",\"postalCode\":\"90210\"}",
                "{\"line1\":\"1 Main St\",\"city\":\"Toronto\",\"country\":\"Canada\",\"province\":\"XX\",\"postalCode\":\"M5V 2A1\"}",
                "{\"line1\":\"\",\"city\":\"Toronto\",\"country\":\"Canada\",\"province\":\"ON\",\"postalCode\":\"M5V 2A1\"}")) {
            MvcTestResult res = put("/api/profile/address", token, "{\"address\":" + address + "}");
            assertThat(res).as(address).hasStatus(400);
            assertThat(json(res, "$.error.code")).isEqualTo("VALIDATION_ERROR");
        }
    }

    // --- preferences ---

    @Test
    void updatesLanguageAndKeepsOtherPreferences() {
        MvcTestResult res = put("/api/preferences", login("david.kim@example.com").accessToken(), "{\"language\":\"fr-CA\"}");
        assertThat(json(res, "$.language")).isEqualTo("fr-CA");
        assertThat(json(res, "$.theme")).isEqualTo("dark");
    }

    @Test
    void mergesAPartialNotificationUpdate() {
        String token = login("robert.foster@example.com").accessToken();
        MvcTestResult before = get("/api/preferences", token);
        boolean sms = read(before, "$.notifications.sms");
        MvcTestResult res = put("/api/preferences", token,
                "{\"notifications\":{\"sms\":%s},\"documentDelivery\":\"paper_and_digital\"}".formatted(!sms));
        assertThat(this.<Boolean>read(res, "$.notifications.sms")).isEqualTo(!sms);
        assertThat(this.<Boolean>read(res, "$.notifications.email")).isEqualTo(read(before, "$.notifications.email"));
        assertThat(this.<Boolean>read(res, "$.notifications.push")).isEqualTo(read(before, "$.notifications.push"));
        assertThat(json(res, "$.documentDelivery")).isEqualTo("paper_and_digital");
    }

    // --- goals ---

    private static String nextYear() {
        return (LocalDate.now(ZoneOffset.UTC).getYear() + 1) + "-06-30";
    }

    @Test
    void listsGoalsInOrder() {
        MvcTestResult res = get("/api/wealth/goals", login().accessToken());
        assertThat(this.<List<String>>read(res, "$.items[*].id")).containsExactly("goal-1", "goal-2", "goal-3");
        assertThat(number(res, "$.total")).isEqualTo(3);
    }

    @Test
    void createsAGoalComputesProgressAndAppendsItToTheList() {
        String token = login("linda.rodriguez@example.com").accessToken();
        MvcTestResult res = postJson("/api/wealth/goals", token, """
                {"name": " New roof ", "category": "Home", "targetAmount": 2000000, "currentAmount": 500000,
                 "currency": "CAD", "targetDate": "%s"}""".formatted(nextYear()));
        assertThat(res).hasStatus(201);
        assertThat(json(res, "$.name")).isEqualTo("New roof");
        assertThat(number(res, "$.progressPct")).isEqualTo(25);
        assertThat(number(res, "$.currentAmount")).isEqualTo(500000);
        String id = json(res, "$.id");
        List<String> ids = read(get("/api/wealth/goals", token), "$.items[*].id");
        assertThat(ids.getLast()).isEqualTo(id);
        // New goals are not added to the dashboard summary.
        assertThat(this.<List<String>>read(get("/api/wealth/dashboard", token), "$.goals[*].id")).doesNotContain(id);
    }

    @Test
    void defaultsCurrentAmountToZero() {
        MvcTestResult res = postJson("/api/wealth/goals", login("emma.wilson@example.com").accessToken(), """
                {"name": "Trip", "category": "Vacation", "targetAmount": 300000, "currency": "CAD",
                 "targetDate": "%s"}""".formatted(nextYear()));
        assertThat(number(res, "$.currentAmount")).isZero();
        assertThat(number(res, "$.progressPct")).isZero();
    }

    @Test
    void rejectsInvalidGoals() {
        String token = login().accessToken();
        String base = "\"category\":\"Home\",\"currency\":\"CAD\"";
        for (String body : List.of(
                "{%s,\"name\":\"\",\"targetAmount\":100000,\"targetDate\":\"%s\"}".formatted(base, nextYear()),
                "{%s,\"name\":\"X\",\"targetAmount\":0,\"targetDate\":\"%s\"}".formatted(base, nextYear()),
                "{%s,\"name\":\"X\",\"targetAmount\":100000,\"currentAmount\":200000,\"targetDate\":\"%s\"}"
                        .formatted(base, nextYear()),
                "{%s,\"name\":\"X\",\"targetAmount\":100000,\"targetDate\":\"2020-01-01\"}".formatted(base),
                "{%s,\"name\":\"X\",\"targetAmount\":100000,\"targetDate\":\"soon\"}".formatted(base),
                "{\"category\":\"Car\",\"currency\":\"CAD\",\"name\":\"X\",\"targetAmount\":100000,\"targetDate\":\"%s\"}"
                        .formatted(nextYear()))) {
            assertThat(postJson("/api/wealth/goals", token, body)).as(body).hasStatus(400);
        }
    }

    // --- notifications ---

    @Test
    void listsNotificationsNewestFirstWithReadState() {
        MvcTestResult res = get("/api/notifications", login().accessToken());
        assertThat(this.<List<String>>read(res, "$[*].id")).containsExactly("no1", "no2", "no3", "no4", "no5");
        assertThat(this.<List<String>>read(res, "$[?(@.read == false)].id")).containsExactly("no1", "no2");
    }

    @Test
    void marksOneNotificationReadAndPersistsIt() {
        String token = login("linda.rodriguez@example.com").accessToken();
        MvcTestResult res = postJson("/api/notifications/no-l1/read", token, "");
        assertThat(res).hasStatusOk();
        assertThat(res).bodyJson().isStrictlyEqualTo("{\"id\":\"no-l1\",\"read\":true}");
        assertThat(this.<List<Boolean>>read(get("/api/notifications", token), "$[*].read")).containsOnly(true);
    }

    @Test
    void returns404ForAnotherUsersNotification() {
        assertThat(postJson("/api/notifications/no1/read", login("emma.wilson@example.com").accessToken(), ""))
                .hasStatus(404);
    }

    @Test
    void marksAllNotificationsRead() {
        String token = login("michael.thompson@example.com").accessToken();
        assertThat(postJson("/api/notifications/read-all", token, "")).bodyJson().isStrictlyEqualTo("{\"updated\":1}");
        assertThat(postJson("/api/notifications/read-all", token, "")).bodyJson().isStrictlyEqualTo("{\"updated\":0}");
    }
}
