package com.electricwealth.api.repository;

import java.util.List;
import java.util.Optional;

import com.electricwealth.api.config.JsonEnums;
import com.electricwealth.api.dto.ApiEnums.HistoryRange;
import com.electricwealth.api.dto.MarketDtos.Basket;
import com.electricwealth.api.dto.MarketDtos.NewsItem;
import com.electricwealth.api.dto.MarketDtos.PricePoint;
import com.electricwealth.api.dto.MarketDtos.Quote;
import com.electricwealth.api.dto.MarketDtos.Risk;
import com.electricwealth.api.dto.MarketDtos.RoundupItem;
import com.electricwealth.api.dto.MarketDtos.RoundupResponse;
import com.electricwealth.api.dto.MarketDtos.SearchItem;
import com.electricwealth.api.dto.MarketDtos.SecurityDetail;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class MarketRepository {

    // held: the user has the symbol in a visible account. watched: it is on their watchlist.
    private static final String QUOTE_SELECT = """
            SELECT s.*,
              EXISTS (SELECT 1 FROM holdings h JOIN accounts a ON a.user_id = h.user_id AND a.id = h.account_id
                      WHERE h.user_id = :userId AND h.symbol = s.symbol AND a.hidden = 0) AS held,
              EXISTS (SELECT 1 FROM watchlist w WHERE w.user_id = :userId AND w.symbol = s.symbol) AS watched
            FROM securities s""";

    private record SecurityRow(String symbol, String name, String exchange, String currency, long price, long change,
            double changePct, @Nullable String about, boolean held, boolean watched) {

        Quote toQuote() {
            return new Quote(symbol, name, exchange, price, currency, change, changePct, held, watched);
        }

        SecurityDetail toDetail() {
            return new SecurityDetail(symbol, name, exchange, price, currency, change, changePct, held, watched, about);
        }
    }

    private final JdbcClient jdbc;

    public MarketRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Quotes for the given symbols (unknown ones are skipped), or for every security when none are given. */
    public List<Quote> listQuotes(String userId, @Nullable List<String> symbols) {
        boolean filtered = symbols != null && !symbols.isEmpty();
        var statement = jdbc.sql(QUOTE_SELECT + (filtered ? " WHERE s.symbol IN (:symbols)" : "") + " ORDER BY s.symbol")
                .param("userId", userId);
        if (filtered) {
            statement = statement.param("symbols", symbols);
        }
        return statement.query(Rows.of(SecurityRow.class)).list().stream()
                .map(SecurityRow::toQuote)
                .toList();
    }

    public Optional<SecurityDetail> findSecurity(String userId, String symbol) {
        return jdbc.sql(QUOTE_SELECT + " WHERE s.symbol = :symbol")
                .param("userId", userId).param("symbol", symbol)
                .query(Rows.of(SecurityRow.class)).optional()
                .map(SecurityRow::toDetail);
    }

    public List<Quote> listWatchlist(String userId) {
        return jdbc.sql(QUOTE_SELECT + """
                 JOIN watchlist w2 ON w2.symbol = s.symbol AND w2.user_id = :userId
                ORDER BY w2.added_at, s.symbol""")
                .param("userId", userId)
                .query(Rows.of(SecurityRow.class)).list().stream()
                .map(SecurityRow::toQuote)
                .toList();
    }

    public void addToWatchlist(String userId, String symbol) {
        jdbc.sql("""
                INSERT INTO watchlist (user_id, symbol, added_at) VALUES (?, ?, strftime('%Y-%m-%dT%H:%M:%SZ', 'now'))
                ON CONFLICT (user_id, symbol) DO NOTHING""")
                .params(userId, symbol).update();
    }

    public void removeFromWatchlist(String userId, String symbol) {
        jdbc.sql("DELETE FROM watchlist WHERE user_id = ? AND symbol = ?").params(userId, symbol).update();
    }

    public List<PricePoint> priceHistory(String symbol, HistoryRange range) {
        return jdbc.sql("SELECT t, close AS c FROM price_history WHERE symbol = ? AND range = ? ORDER BY t")
                .params(symbol, JsonEnums.value(range)).query(Rows.of(PricePoint.class)).list();
    }

    public List<SearchItem> search(@Nullable String q) {
        String like = SqlValues.containsPattern(q);
        return jdbc.sql("""
                SELECT symbol, name, exchange, currency FROM securities
                WHERE symbol LIKE ? ESCAPE '\\' OR name LIKE ? ESCAPE '\\'
                ORDER BY symbol""")
                .params(like, like).query(Rows.of(SearchItem.class)).list();
    }

    public List<NewsItem> listNews() {
        return jdbc.sql("SELECT * FROM market_news ORDER BY published_at DESC").query(Rows.of(NewsItem.class)).list();
    }

    /** The most recent week's roundup. */
    public RoundupResponse latestRoundup() {
        // MAX() returns one row holding NULL when the table is empty.
        String week = jdbc.sql("SELECT MAX(week_of) FROM market_roundup_items").query(String.class).list().getFirst();
        List<RoundupItem> items = jdbc.sql("SELECT * FROM market_roundup_items WHERE week_of = ? ORDER BY published_at DESC")
                .param(week).query(Rows.of(RoundupItem.class)).list();
        return new RoundupResponse(week != null ? week : "", items);
    }

    public List<Basket> listBaskets() {
        // Mapped by hand: "return1YPct" doesn't follow the column-naming rule Rows relies on.
        return jdbc.sql("SELECT * FROM baskets ORDER BY sort_order, id")
                .query((rs, i) -> new Basket(rs.getString("id"), rs.getString("name"), rs.getString("description"),
                        rs.getDouble("return_1y_pct"), rs.getInt("holdings_count"),
                        JsonEnums.parse(Risk.class, rs.getString("risk"))))
                .list();
    }
}
