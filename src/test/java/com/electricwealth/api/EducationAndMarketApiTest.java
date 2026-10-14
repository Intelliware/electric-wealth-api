package com.electricwealth.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Ported from the "education" and "market" tests in test/app.test.ts. */
class EducationAndMarketApiTest extends ApiTestBase {

    // --- education ---

    @Test
    void listsTheCatalogueWithTheUsersProgress() {
        MvcTestResult res = get("/api/education/content", login().accessToken());
        assertThat(this.<List<String>>read(res, "$[*].id")).containsExactly("e1", "e2", "e3", "e4", "e5", "e6");
        assertThat(this.<List<Integer>>read(res, "$[*].progressPct")).containsExactly(100, 60, 0, 0, 0, 0);
    }

    @Test
    void filtersByLevelAndTypeAndLocalizesTitles() {
        MvcTestResult res = get("/api/education/content?level=beginner&type=challenge", login().accessToken(), "fr-CA");
        assertThat(this.<List<String>>read(res, "$[*].id")).containsExactly("e3");
        assertThat(json(res, "$[0].title")).isEqualTo("CELI ou REER : lequel en premier?");
    }

    @Test
    void savesProgressAndCreatesARowTheFirstTime() {
        String token = login("robert.foster@example.com").accessToken();
        MvcTestResult res = postJson("/api/education/progress", token, "{\"contentId\":\"e4\",\"progressPct\":100}");
        assertThat(res).bodyJson().isStrictlyEqualTo("{\"contentId\":\"e4\",\"progressPct\":100}");
        assertThat(this.<List<Integer>>read(get("/api/education/content", token), "$[?(@.id == 'e4')].progressPct"))
                .containsExactly(100);
    }

    @Test
    void rejectsUnknownContentAndOutOfRangeProgress() {
        String token = login().accessToken();
        assertThat(postJson("/api/education/progress", token, "{\"contentId\":\"zz\",\"progressPct\":10}")).hasStatus(404);
        assertThat(postJson("/api/education/progress", token, "{\"contentId\":\"e1\",\"progressPct\":101}")).hasStatus(400);
    }

    @Test
    void searchesTheGlossaryInTheRequestedLanguage() {
        String token = login().accessToken();
        List<String> all = read(get("/api/education/glossary", token), "$[*].term");
        assertThat(all).hasSize(7);
        assertThat(all.getFirst()).isEqualTo("Asset allocation");
        assertThat(this.<List<String>>read(get("/api/education/glossary?q=tax", token), "$[*].term"))
                .containsExactly("RRSP", "TFSA");
        assertThat(this.<List<String>>read(get("/api/education/glossary?q=celi", token, "fr-CA"), "$[*].term"))
                .containsExactly("CELI");
        List<String> french = read(get("/api/education/glossary", token, "fr-CA"), "$[*].term");
        assertThat(french.subList(french.size() - 4, french.size()))
                .containsExactly("Rééquilibrage", "REER", "Répartition de l’actif", "Taux de rendement");
    }

    // --- market ---

    @Test
    void flagsSecuritiesTheUserHoldsInAVisibleAccountAndHasOnTheWatchlist() {
        MvcTestResult res = get("/api/market/quote?symbols=xeqt.to,AAPL,NOPE", login().accessToken());
        assertThat(this.<List<String>>read(res, "$[*].symbol")).containsExactly("AAPL", "XEQT.TO");
        assertThat(this.<List<Boolean>>read(res, "$[*].held")).containsExactly(false, true);
        assertThat(this.<List<Boolean>>read(res, "$[*].watched")).containsExactly(true, true);
    }

    @Test
    void returnsASecurityWithItsDescriptionOr404() {
        String token = login().accessToken();
        MvcTestResult zag = get("/api/market/securities/zag.to", token);
        assertThat(this.<Map<String, Object>>read(zag, "$")).containsEntry("symbol", "ZAG.TO")
                .containsEntry("held", true).containsEntry("watched", false).containsEntry("currency", "CAD");
        assertThat(json(zag, "$.about")).isNotBlank();
        assertThat(get("/api/market/securities/NOPE", token)).hasStatus(404);
    }

    @Test
    void returnsHistoryThatEndsAtTheCurrentPrice() {
        String token = login().accessToken();
        for (String range : List.of("1D", "1M", "1Y", "5Y")) {
            List<Integer> closes = read(get("/api/market/history?symbol=VFV.TO&range=" + range, token), "$.series[*].c");
            assertThat(closes).as(range).hasSizeGreaterThan(10);
            assertThat(closes.getLast()).as(range).isEqualTo(14210);
        }
        assertThat(get("/api/market/history?symbol=VFV.TO&range=2Y", token)).hasStatus(400);
    }

    @Test
    void searchesBySymbolOrName() {
        MvcTestResult res = get("/api/market/search?q=bank", login().accessToken());
        assertThat(this.<List<String>>read(res, "$.items[*].symbol")).containsExactly("NA.TO");
    }

    @Test
    void addsToAndRemovesFromTheWatchlist() {
        String token = login("david.kim@example.com").accessToken();
        // Seeded entries share a timestamp, so they sort by symbol; the new one comes last.
        assertThat(this.<List<String>>read(put("/api/market/watchlist/ZAG.TO", token, ""), "$[*].symbol"))
                .containsExactly("AAPL", "NA.TO", "SHOP.TO", "VFV.TO", "XEQT.TO", "ZAG.TO");
        assertThat(this.<List<?>>read(put("/api/market/watchlist/ZAG.TO", token, ""), "$")).hasSize(6);
        assertThat(this.<List<String>>read(delete("/api/market/watchlist/AAPL", token), "$[*].symbol"))
                .doesNotContain("AAPL");
        assertThat(put("/api/market/watchlist/NOPE", token, "")).hasStatus(404);
    }

    @Test
    void servesNewsTheWeeklyRoundupAndBaskets() {
        String token = login().accessToken();
        assertThat(json(get("/api/market/news", token), "$[0].id")).isEqualTo("n1");
        assertThat(json(get("/api/market/roundup", token), "$.weekOf")).isEqualTo("2024-12-01");
        assertThat(this.<List<?>>read(get("/api/market/baskets", token), "$")).hasSize(4);
    }
}
