package com.electricwealth.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Ported from the "health" and "auth" tests in electric-wealth-api's test/app.test.ts, plus error-shape checks. */
class AuthApiTest extends ApiTestBase {

    @Test
    void healthReturnsOkWithoutAuth() {
        assertThat(mvc.get().uri("/health")).hasStatusOk().bodyJson().isStrictlyEqualTo("{\"status\":\"ok\"}");
    }

    @Test
    void passwordLoginPlusMfaIssuesAnAccessToken() {
        assertThat(login().accessToken()).startsWith("mock-at-");
    }

    @Test
    void rejectsInvalidCredentialsWithTheErrorEnvelope() {
        MvcTestResult res = postJson("/api/auth/token",
                "{\"grantType\":\"password\",\"username\":\"sarah.chen@example.com\",\"password\":\"wrong\"}");
        assertThat(res).hasStatus(401);
        assertThat(json(res, "$.error.code")).isEqualTo("INVALID_CREDENTIALS");
        assertThat(json(res, "$.error.traceId")).startsWith("trace-");
        assertThat(res).headers().containsHeader("X-Trace-Id");
    }

    @Test
    void rejectsAWrongMfaCode() {
        MvcTestResult tokenRes = passwordLogin("sarah.chen@example.com");
        MvcTestResult res = postJson("/api/auth/mfa/verify", json(tokenRes, "$.accessToken"),
                "{\"challengeId\":\"mfa-1\",\"code\":\"000000\",\"method\":\"sms\"}");
        assertThat(res).hasStatus(401);
        assertThat(json(res, "$.error.code")).isEqualTo("INVALID_MFA");
    }

    @Test
    void doesNotAcceptATokenThatHasNotCompletedMfa() {
        String accessToken = json(passwordLogin("sarah.chen@example.com"), "$.accessToken");
        assertThat(mvc.get().uri("/api/wealth/dashboard").header("Authorization", "Bearer " + accessToken))
                .hasStatus(401);
    }

    @Test
    void returns401ForAProtectedRouteWithoutABearerToken() {
        MvcTestResult res = mvc.get().uri("/api/wealth/dashboard").exchange();
        assertThat(res).hasStatus(401);
        assertThat(json(res, "$.error.code")).isEqualTo("UNAUTHORIZED");
    }

    @Test
    void rotatesTokensOnRefreshAndRevokesTheOldRefreshToken() {
        String body = "{\"refreshToken\":\"%s\"}".formatted(login("emma.wilson@example.com").refreshToken());
        MvcTestResult first = postJson("/api/auth/refresh", body);
        assertThat(first).hasStatusOk();
        assertThat(json(first, "$.accessToken")).startsWith("mock-at-");
        assertThat(postJson("/api/auth/refresh", body)).hasStatus(401);
    }

    @Test
    void logoutRevokesTheAccessToken() {
        String accessToken = login().accessToken();
        assertThat(mvc.post().uri("/api/auth/logout").header("Authorization", "Bearer " + accessToken))
                .hasStatus(204);
        assertThat(mvc.get().uri("/api/wealth/dashboard").header("Authorization", "Bearer " + accessToken))
                .hasStatus(401);
    }

    @Test
    void reportsEachInvalidFieldAsAValidationError() {
        MvcTestResult res = postJson("/api/auth/token", "{\"grantType\":\"nope\",\"username\":\"not-an-email\"}");
        assertThat(res).hasStatus(400);
        assertThat(json(res, "$.error.code")).isEqualTo("VALIDATION_ERROR");
        assertThat(json(res, "$.error.message")).contains("grantType", "username", "password");
    }

    @Test
    void namesTheFieldWhenAnEnumValueIsUnknown() {
        String accessToken = json(passwordLogin("sarah.chen@example.com"), "$.accessToken");
        MvcTestResult res = postJson("/api/auth/mfa/verify", accessToken,
                "{\"challengeId\":\"mfa-1\",\"code\":\"123456\",\"method\":\"fax\"}");
        assertThat(res).hasStatus(400);
        assertThat(json(res, "$.error.message")).isEqualTo("Invalid request. method: invalid value");
    }

    @Test
    void returnsTheErrorEnvelopeForAnUnknownRoute() {
        MvcTestResult res = mvc.get().uri("/api/nope").exchange();
        assertThat(res).hasStatus(404);
        assertThat(json(res, "$.error.message")).isEqualTo("No route for GET /api/nope.");
    }
}
