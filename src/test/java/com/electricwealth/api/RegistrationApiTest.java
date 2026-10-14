package com.electricwealth.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Ported from the "registration" tests in test/app.test.ts. */
class RegistrationApiTest extends ApiTestBase {

    private static final String PRIYA = "\"accountNumber\":\"7391046285\",\"postalCode\":\"k1p5g4\",\"dateOfBirth\":\"1990-02-17\"";
    private static final String CONSENTS = "{\"terms\":true,\"privacy\":true,\"electronicDelivery\":true}";

    private String start(String email) {
        MvcTestResult res = postJson("/api/registration/start",
                "{\"email\":\"%s\",\"password\":\"Welcome2026\"}".formatted(email));
        assertThat(res).hasStatus(201);
        return json(res, "$.registrationId");
    }

    private MvcTestResult complete(String registrationId, String mfaMethod, String consents) {
        return postJson("/api/registration/complete",
                "{\"registrationId\":\"%s\",\"mfaMethod\":\"%s\",\"consents\":%s}".formatted(registrationId, mfaMethod, consents));
    }

    @Test
    void enrolsAnExistingClientEndToEndAndLetsThemSignIn() {
        String registrationId = start("priya.patel@example.com");

        MvcTestResult verified = postJson("/api/registration/verify-identity",
                "{\"registrationId\":\"%s\",%s}".formatted(registrationId, PRIYA));
        assertThat(verified).bodyJson().isStrictlyEqualTo("{\"verified\":true,\"name\":\"Priya Patel\"}");

        assertThat(this.<List<?>>read(mvc.get().uri("/api/registration/security-questions").exchange(), "$")).hasSize(5);
        assertThat(postJson("/api/registration/security-questions", """
                {"registrationId": "%s", "answers": [{"questionId": "q1", "answer": "Biscuit"},
                                                     {"questionId": "q2", "answer": "Ottawa"}]}""".formatted(registrationId)))
                .bodyJson().isStrictlyEqualTo("{\"saved\":true}");
        assertThat(postJson("/api/registration/trusted-contact",
                "{\"registrationId\":\"%s\",\"name\":\"Anil Patel\",\"relationship\":\"Brother\"}".formatted(registrationId)))
                .hasStatusOk();

        MvcTestResult done = complete(registrationId, "email", CONSENTS);
        assertThat(done).hasStatus(201);
        assertThat(this.<Boolean>read(done, "$.mfaRequired")).isFalse();
        String token = json(done, "$.accessToken");

        // The returned session works immediately, and the new user has a profile and a dashboard.
        MvcTestResult profile = get("/api/profile", token);
        assertThat(json(profile, "$.name")).isEqualTo("Priya Patel");
        assertThat(json(profile, "$.email")).isEqualTo("priya.patel@example.com");
        assertThat(json(profile, "$.trustedContact.name")).isEqualTo("Anil Patel");
        assertThat(json(profile, "$.trustedContact.relationship")).isEqualTo("Brother");
        assertThat(json(profile, "$.address.city")).isEqualTo("Ottawa");
        MvcTestResult dashboard = get("/api/wealth/dashboard", token);
        assertThat(number(dashboard, "$.portfolio.totalValue")).isZero();
        assertThat(this.<List<String>>read(dashboard, "$.quickLinks[*].deepLink")).containsExactly(
                "electricwealth://accounts/new", "electricwealth://advisor", "electricwealth://learn");
        assertThat(json(get("/api/notifications", token), "$[0].id")).isEqualTo("welcome");
        assertThat(json(get("/api/advisor/availability", token), "$.advisor.name")).isEqualTo("Jordan Avery");

        // Normal sign-in with the chosen password works too.
        assertThat(postJson("/api/auth/token",
                "{\"grantType\":\"password\",\"username\":\"priya.patel@example.com\",\"password\":\"Welcome2026\"}"))
                .hasStatusOk();

        // The registration is used up and the client cannot enrol twice.
        assertThat(complete(registrationId, "sms", CONSENTS)).hasStatus(404);
        String again = start("priya.other@example.com");
        MvcTestResult dup = postJson("/api/registration/verify-identity",
                "{\"registrationId\":\"%s\",%s}".formatted(again, PRIYA));
        assertThat(dup).hasStatus(409);
        assertThat(json(dup, "$.error.code")).isEqualTo("ALREADY_ENROLLED");
    }

    @Test
    void usesTheBranchRecordLanguageForAFrenchSpeakingClient() {
        String registrationId = start("marc.tremblay@example.com");
        postJson("/api/registration/verify-identity", """
                {"registrationId": "%s", "accountNumber": "2654819370", "postalCode": "H2X 1Y4",
                 "dateOfBirth": "1985-09-03"}""".formatted(registrationId));
        postJson("/api/registration/security-questions", """
                {"registrationId": "%s", "answers": [{"questionId": "q3", "answer": "Lada"},
                                                     {"questionId": "q5", "answer": "rue Rachel"}]}""".formatted(registrationId));
        String token = json(complete(registrationId, "sms", CONSENTS), "$.accessToken");
        MvcTestResult prefs = get("/api/preferences", token);
        assertThat(json(prefs, "$.language")).isEqualTo("fr-CA");
        assertThat(this.<Boolean>read(prefs, "$.notifications.sms")).isTrue();
        assertThat(get("/api/profile", token)).bodyJson().doesNotHavePath("$.trustedContact");
    }

    @Test
    void rejectsTakenEmailsWeakPasswordsAndMismatchedIdentity() {
        assertThat(json(postJson("/api/registration/start",
                "{\"email\":\"sarah.chen@example.com\",\"password\":\"Welcome2026\"}"), "$.error.code"))
                .isEqualTo("EMAIL_TAKEN");
        assertThat(postJson("/api/registration/start", "{\"email\":\"new@example.com\",\"password\":\"short\"}"))
                .hasStatus(400);
        assertThat(postJson("/api/registration/start", "{\"email\":\"new@example.com\",\"password\":\"lettersonly\"}"))
                .hasStatus(400);
        String registrationId = start("someone@example.com");
        MvcTestResult wrong = postJson("/api/registration/verify-identity", """
                {"registrationId": "%s", "accountNumber": "7391046285", "postalCode": "k1p5g4",
                 "dateOfBirth": "1990-02-18"}""".formatted(registrationId));
        assertThat(wrong).hasStatus(422);
        assertThat(json(wrong, "$.error.code")).isEqualTo("IDENTITY_MISMATCH");
        MvcTestResult sarah = postJson("/api/registration/verify-identity", """
                {"registrationId": "%s", "accountNumber": "4829157302", "postalCode": "M5V 2A1",
                 "dateOfBirth": "1996-04-12"}""".formatted(registrationId));
        assertThat(json(sarah, "$.error.code")).isEqualTo("ALREADY_ENROLLED");
    }

    @Test
    void enforcesTheStepOrderAndTheConsents() {
        String registrationId = start("order@example.com");
        MvcTestResult early = postJson("/api/registration/security-questions", """
                {"registrationId": "%s", "answers": [{"questionId": "q1", "answer": "Rex"},
                                                     {"questionId": "q2", "answer": "Paris"}]}""".formatted(registrationId));
        assertThat(json(early, "$.error.code")).isEqualTo("STEP_OUT_OF_ORDER");
        assertThat(json(complete(registrationId, "sms", CONSENTS), "$.error.code")).isEqualTo("STEP_OUT_OF_ORDER");
        assertThat(complete(registrationId, "sms", "{\"terms\":true,\"privacy\":false,\"electronicDelivery\":true}"))
                .hasStatus(400);
        assertThat(postJson("/api/registration/security-questions", """
                {"registrationId": "%s", "answers": [{"questionId": "q1", "answer": "Rex"},
                                                     {"questionId": "q1", "answer": "Max"}]}""".formatted(registrationId)))
                .hasStatus(400);
        assertThat(postJson("/api/registration/verify-identity", "{\"registrationId\":\"reg-nope\",%s}".formatted(PRIYA)))
                .hasStatus(404);
    }
}
