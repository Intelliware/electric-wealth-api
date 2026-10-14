package com.electricwealth.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.jayway.jsonpath.JsonPath;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Tests run against a fresh copy of the database, built once per test run by the sibling electric-wealth-db
 * repo's reset script (the same approach as the Node API's tests). Requests go through the full filter chain,
 * including Spring Security, via MockMvc.
 */
@SpringBootTest(properties = { "ew.log-requests=false", "ew.rate-limit-per-minute=100000" })
@AutoConfigureMockMvc
public abstract class ApiTestBase {

    protected static final String PASSWORD = "Demo123!";

    private static final Path DB_PATH = createTestDatabase();

    @Autowired
    protected MockMvcTester mvc;

    /** For checking what was written to the database. */
    @Autowired
    protected JdbcClient jdbc;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("ew.db-path", DB_PATH::toString);
    }

    private static Path createTestDatabase() {
        Path resetScript = Path.of("../electric-wealth-db/scripts/reset.js").toAbsolutePath().normalize();
        if (!Files.exists(resetScript)) {
            throw new IllegalStateException("Cannot find " + resetScript + ". Clone electric-wealth-db next to this repo.");
        }
        try {
            Path dir = Files.createTempDirectory("ew-api-spring-test-");
            Path db = dir.resolve("test.db");
            Process process = new ProcessBuilder("node", resetScript.toString(), "--db", db.toString())
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            if (process.waitFor() != 0) {
                throw new IllegalStateException("Database reset script failed (is Node.js on the PATH?)");
            }
            dir.toFile().deleteOnExit();
            db.toFile().deleteOnExit();
            return db;
        } catch (IOException e) {
            throw new IllegalStateException("Could not build the test database (is Node.js on the PATH?)", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    protected record Tokens(String accessToken, String refreshToken) {
    }

    protected MvcTestResult get(String uri, String accessToken) {
        return mvc.get().uri(uri).header("Authorization", "Bearer " + accessToken).exchange();
    }

    protected MvcTestResult get(String uri, String accessToken, String acceptLanguage) {
        return mvc.get().uri(uri).header("Authorization", "Bearer " + accessToken)
                .header("Accept-Language", acceptLanguage).exchange();
    }

    protected MvcTestResult put(String uri, String accessToken, String json) {
        return mvc.put().uri(uri).header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    protected MvcTestResult patch(String uri, String accessToken, String json) {
        return mvc.patch().uri(uri).header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    protected MvcTestResult delete(String uri, String accessToken) {
        return mvc.delete().uri(uri).header("Authorization", "Bearer " + accessToken).exchange();
    }

    /** Reads a JSON path from the response body: a String, Number, Boolean, List or Map. */
    @SuppressWarnings("unchecked")
    protected static <T> T read(MvcTestResult result, String path) {
        try {
            return (T) JsonPath.read(result.getResponse().getContentAsString(), path);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    protected static long number(MvcTestResult result, String path) {
        return ((Number) read(result, path)).longValue();
    }

    protected MvcTestResult postJson(String uri, String json) {
        return mvc.post().uri(uri).contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    protected MvcTestResult postJson(String uri, String accessToken, String json) {
        return mvc.post().uri(uri).header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    protected static String json(MvcTestResult result, String path) {
        try {
            return JsonPath.read(result.getResponse().getContentAsString(), path).toString();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    protected MvcTestResult passwordLogin(String email) {
        return postJson("/api/auth/token",
                "{\"grantType\":\"password\",\"username\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD));
    }

    /** Password login followed by MFA: a fully signed-in session. */
    protected Tokens login(String email) {
        MvcTestResult tokenRes = passwordLogin(email);
        assertThat(tokenRes).hasStatusOk();
        assertThat(json(tokenRes, "$.mfaRequired")).isEqualTo("true");

        MvcTestResult mfaRes = postJson("/api/auth/mfa/verify", json(tokenRes, "$.accessToken"),
                "{\"challengeId\":\"mfa-1\",\"code\":\"123456\",\"method\":\"sms\"}");
        assertThat(mfaRes).hasStatusOk();
        return new Tokens(json(mfaRes, "$.accessToken"), json(tokenRes, "$.refreshToken"));
    }

    protected Tokens login() {
        return login("sarah.chen@example.com");
    }
}
