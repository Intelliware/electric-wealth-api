package com.electricwealth.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Ported from the "dashboard", "accounts", "documents" and "document sharing" tests in test/app.test.ts. */
class WealthApiTest extends ApiTestBase {

    private static final List<String> PERSONAS = List.of(
            "sarah.chen@example.com",
            "michael.thompson@example.com",
            "linda.rodriguez@example.com",
            "david.kim@example.com",
            "emma.wilson@example.com",
            "robert.foster@example.com");

    // --- dashboard ---

    @Test
    void returnsThePersonaAlignedDashboardForSarahChen() {
        MvcTestResult res = get("/api/wealth/dashboard", login().accessToken());
        assertThat(res).hasStatusOk();
        assertThat(json(res, "$.portfolio.currency")).isEqualTo("CAD");
        assertThat(number(res, "$.portfolio.totalValue")).isEqualTo(6025000);
        assertThat(res).bodyJson().extractingPath("$.goals")
                .isEqualTo(List.of(Map.of("id", "goal-1", "name", "Home Down Payment", "progressPct", 48)));
    }

    @Test
    void returnsAValidDashboardForAllSixDemoPersonas() {
        for (String email : PERSONAS) {
            MvcTestResult res = get("/api/wealth/dashboard", login(email).accessToken());
            assertThat(res).as(email).hasStatusOk();
            assertThat(this.<List<?>>read(res, "$.quickLinks")).as(email).isNotNull();
        }
    }

    @Test
    void localizesQuickLinkLabelsForFrench() {
        MvcTestResult res = get("/api/wealth/dashboard", login().accessToken(), "fr-CA");
        assertThat(json(res, "$.quickLinks[0].label")).isEqualTo("Ouvrir un compte");
    }

    // --- accounts ---

    @Test
    void listsVisibleAccountsOnly() {
        // Linda has a hidden RRIF.
        MvcTestResult res = get("/api/wealth/accounts", login("linda.rodriguez@example.com").accessToken());
        assertThat(number(res, "$.total")).isEqualTo(4);
        assertThat(this.<List<String>>read(res, "$.items[*].id")).doesNotContain("rrif");
    }

    @Test
    void returnsAccountDetailWithHoldings() {
        MvcTestResult res = get("/api/wealth/accounts/tfsa", login().accessToken());
        assertThat(json(res, "$.displayName")).isEqualTo("Growth TFSA");
        assertThat(this.<List<?>>read(res, "$.holdings")).hasSize(3);
    }

    @Test
    void returns404ForAnAccountBelongingToNobody() {
        assertThat(get("/api/wealth/accounts/nope", login().accessToken())).hasStatus(404);
    }

    @Test
    void pagesTransactionsAndIncludesScheduledOnes() {
        MvcTestResult res = get("/api/wealth/accounts/tfsa/transactions?page=1&pageSize=5", login().accessToken());
        assertThat(number(res, "$.total")).isEqualTo(7);
        assertThat(this.<List<?>>read(res, "$.items")).hasSize(5);
        assertThat(json(res, "$.items[0].id")).isEqualTo("t1");
        assertThat(this.<List<?>>read(res, "$.scheduled")).hasSize(1);
    }

    @Test
    void filtersTransactionsByType() {
        MvcTestResult res = get("/api/wealth/accounts/tfsa/transactions?type=dividend", login().accessToken());
        assertThat(this.<List<String>>read(res, "$.items[*].type")).isNotEmpty().containsOnly("dividend");
    }

    @Test
    void rejectsAnInvalidQueryParameter() {
        MvcTestResult res = get("/api/wealth/accounts/tfsa/transactions?type=bogus", login().accessToken());
        assertThat(res).hasStatus(400);
        assertThat(json(res, "$.error.code")).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void opensANewEmptyAccountWithTypeSpecificRules() {
        String token = login("emma.wilson@example.com").accessToken();
        MvcTestResult res = postJson("/api/wealth/accounts", token,
                "{\"type\":\"RRSP\",\"displayName\":\" Spousal RRSP \"}");
        assertThat(res).hasStatus(201);
        assertThat(json(res, "$.type")).isEqualTo("RRSP");
        assertThat(json(res, "$.displayName")).isEqualTo("Spousal RRSP");
        assertThat(number(res, "$.balance")).isZero();
        assertThat(json(res, "$.taxStatus")).isEqualTo("Tax-deferred");
        assertThat(res).bodyJson().extractingPath("$.contributionRoom").isNull();
        String id = json(res, "$.id");

        List<String> ids = read(get("/api/wealth/accounts", token), "$.items[*].id");
        assertThat(ids.getLast()).isEqualTo(id);
        assertThat(this.<List<?>>read(get("/api/wealth/accounts/" + id, token), "$.holdings")).isEmpty();
        MvcTestResult perf = get("/api/wealth/accounts/" + id + "/performance", token);
        assertThat(perf).hasStatusOk();
        assertThat(this.<List<?>>read(perf, "$.series")).isEmpty();
        assertThat(number(get("/api/wealth/accounts/" + id + "/transactions", token), "$.total")).isZero();
    }

    @Test
    void givesANewTfsaTheAnnualContributionRoom() {
        String token = login("emma.wilson@example.com").accessToken();
        MvcTestResult res = postJson("/api/wealth/accounts", token, "{\"type\":\"TFSA\",\"displayName\":\"Second TFSA\"}");
        assertThat(json(res, "$.taxStatus")).isEqualTo("Tax-free");
        assertThat(number(res, "$.contributionRoom")).isEqualTo(700000);
    }

    @Test
    void rejectsAccountTypesThatNeedAnAdvisor() {
        String token = login().accessToken();
        for (String body : List.of("{\"type\":\"RESP\",\"displayName\":\"Kids\"}",
                "{\"type\":\"TFSA\",\"displayName\":\"\"}")) {
            assertThat(postJson("/api/wealth/accounts", token, body)).as(body).hasStatus(400);
        }
    }

    @Test
    void persistsANicknameChangeToTheDatabase() {
        String token = login("michael.thompson@example.com").accessToken();
        assertThat(patch("/api/wealth/accounts/tfsa", token, "{\"nickname\":\"Rainy day\"}")).hasStatusOk();
        String nickname = jdbc.sql("SELECT nickname FROM accounts WHERE user_id = 'michael' AND id = 'tfsa'")
                .query(String.class).single();
        assertThat(nickname).isEqualTo("Rainy day");
    }

    @Test
    void clearsANicknameWithNullAndLeavesItAloneWhenAbsent() {
        String token = login("michael.thompson@example.com").accessToken();
        patch("/api/wealth/accounts/rrsp", token, "{\"nickname\":\"Later\"}");
        assertThat(json(patch("/api/wealth/accounts/rrsp", token, "{\"hidden\":false}"), "$.nickname"))
                .isEqualTo("Later");
        MvcTestResult cleared = patch("/api/wealth/accounts/rrsp", token, "{\"nickname\":null}");
        assertThat(cleared).bodyJson().extractingPath("$.nickname").isNull();
    }

    // --- documents ---

    @Test
    void listsAndFiltersDocuments() {
        String token = login().accessToken();
        assertThat(number(get("/api/wealth/documents", token), "$.total")).isEqualTo(7);
        MvcTestResult tax2024 = get("/api/wealth/documents?year=2024&category=Tax", token);
        assertThat(this.<List<String>>read(tax2024, "$.items[*].id")).containsExactly("d1");
    }

    @Test
    void returnsACompletePdfForDownload() {
        MvcTestResult res = get("/api/wealth/documents/d1", login().accessToken());
        assertThat(res).hasStatusOk();
        String pdf = new String(Base64.getDecoder().decode(json(res, "$.base64Stub")),
                java.nio.charset.StandardCharsets.ISO_8859_1);
        assertThat(pdf).startsWith("%PDF-1.4\n").contains("(" + json(res, "$.name") + ") Tj").endsWith("%%EOF\n");
    }

    @Test
    void recordsASevenDayShare() {
        String token = login().accessToken();
        Instant before = Instant.now();
        MvcTestResult res = postJson("/api/wealth/documents/d1/share", token, "{\"recipient\":\"accountant@example.com\"}");
        assertThat(res).hasStatusOk();
        assertThat(json(res, "$.recipient")).isEqualTo("accountant@example.com");
        double days = (Instant.parse(json(res, "$.expiresAt")).toEpochMilli() - before.toEpochMilli()) / 86_400_000.0;
        assertThat(days).isBetween(6.99, 7.01);
        String recipient = jdbc.sql("SELECT recipient FROM document_shares WHERE user_id = 'sarah' AND document_id = 'd1'")
                .query(String.class).list().getFirst();
        assertThat(recipient).isEqualTo("accountant@example.com");
    }

    @Test
    void validatesTheShareRecipientAndTheDocument() {
        String token = login().accessToken();
        assertThat(postJson("/api/wealth/documents/d1/share", token, "{\"recipient\":\"nope\"}")).hasStatus(400);
        assertThat(postJson("/api/wealth/documents/zzz/share", token, "{\"recipient\":\"a@example.com\"}"))
                .hasStatus(404);
    }
}
