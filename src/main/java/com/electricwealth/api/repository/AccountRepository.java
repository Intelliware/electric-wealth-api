package com.electricwealth.api.repository;

import static com.electricwealth.api.repository.SqlValues.offset;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.electricwealth.api.config.JsonEnums;
import com.electricwealth.api.dto.AccountDtos.AccountOpenRequest;
import com.electricwealth.api.dto.AccountDtos.AccountPatchRequest;
import com.electricwealth.api.dto.AccountDtos.AccountSummary;
import com.electricwealth.api.dto.AccountDtos.Holding;
import com.electricwealth.api.dto.AccountDtos.PerformancePoint;
import com.electricwealth.api.dto.AccountDtos.Transaction;
import com.electricwealth.api.dto.ApiEnums.OpenableAccountType;
import com.electricwealth.api.dto.ApiEnums.TransactionType;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class AccountRepository {

    public record PerformanceRecord(double timeWeightedReturnPct, String currency, String benchmarkName,
            List<PerformancePoint> series, List<PerformancePoint> benchmarkSeries) {
    }

    /** Tax treatment and starting contribution room (cents) for a newly opened account. */
    private record NewAccountRules(String taxStatus, @Nullable Long contributionRoom) {

        static NewAccountRules of(OpenableAccountType type) {
            return switch (type) {
                case TFSA -> new NewAccountRules("Tax-free", 700_000L); // 2024 annual limit, $7,000
                case FHSA -> new NewAccountRules("Tax-free", 800_000L); // annual limit, $8,000
                case RRSP -> new NewAccountRules("Tax-deferred", null); // depends on income; set later
                case NON_REGISTERED -> new NewAccountRules("Taxable", null);
            };
        }
    }

    private final JdbcClient jdbc;

    public AccountRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public PageResult<AccountSummary> listVisible(String userId, int page, int pageSize) {
        long total = jdbc.sql("SELECT COUNT(*) FROM accounts WHERE user_id = ? AND hidden = 0")
                .param(userId).query(Long.class).single();
        List<AccountSummary> items = jdbc.sql("""
                SELECT * FROM accounts WHERE user_id = ? AND hidden = 0 ORDER BY sort_order LIMIT ? OFFSET ?""")
                .params(userId, pageSize, offset(page, pageSize))
                .query(Rows.of(AccountSummary.class)).list();
        return new PageResult<>(items, total);
    }

    public Optional<AccountSummary> find(String userId, String accountId) {
        return jdbc.sql("SELECT * FROM accounts WHERE user_id = ? AND id = ?")
                .params(userId, accountId).query(Rows.of(AccountSummary.class)).optional();
    }

    /** Sum of visible account balances, used for the dashboard portfolio total. */
    public long totalVisibleBalance(String userId) {
        return jdbc.sql("SELECT COALESCE(SUM(balance), 0) FROM accounts WHERE user_id = ? AND hidden = 0")
                .param(userId).query(Long.class).single();
    }

    public void update(String userId, String accountId, AccountPatchRequest patch) {
        if (patch.isNicknameSent()) {
            jdbc.sql("UPDATE accounts SET nickname = ? WHERE user_id = ? AND id = ?")
                    .params(patch.getNickname(), userId, accountId).update();
        }
        if (patch.getHidden() != null) {
            jdbc.sql("UPDATE accounts SET hidden = ? WHERE user_id = ? AND id = ?")
                    .params(SqlValues.toInt(patch.getHidden()), userId, accountId).update();
        }
    }

    /** Opens an empty account at the end of the user's list and returns its id. */
    @Transactional
    public String open(String userId, AccountOpenRequest input) {
        String id = "acct-" + UUID.randomUUID().toString().substring(0, 8);
        var rules = NewAccountRules.of(input.type());
        int next = jdbc.sql("SELECT COALESCE(MAX(sort_order), 0) + 1 FROM accounts WHERE user_id = ?")
                .param(userId).query(Integer.class).single();
        jdbc.sql("""
                INSERT INTO accounts (user_id, id, type, display_name, nickname, balance, currency, hidden, day_change,
                                      day_change_pct, contribution_room, tax_status, rate_of_return, sort_order)
                VALUES (?, ?, ?, ?, ?, 0, 'CAD', 0, 0, 0, ?, ?, 0, ?)""")
                .params(userId, id, JsonEnums.value(input.type()), input.displayName(),
                        SqlValues.blankToNull(input.nickname()), rules.contributionRoom(), rules.taxStatus(), next)
                .update();
        // An empty performance record, so the account page shows "no history yet" rather than an error.
        jdbc.sql("""
                INSERT INTO account_performance (user_id, account_id, time_weighted_return_pct, currency, benchmark_name)
                VALUES (?, ?, 0, 'CAD', 'S&P/TSX Composite')""")
                .params(userId, id).update();
        return id;
    }

    public List<Holding> listHoldings(String userId, String accountId) {
        return jdbc.sql("SELECT * FROM holdings WHERE user_id = ? AND account_id = ? ORDER BY weight_pct DESC, symbol")
                .params(userId, accountId).query(Rows.of(Holding.class)).list();
    }

    public PageResult<Transaction> listPostedTransactions(String userId, String accountId, int page, int pageSize,
            @Nullable TransactionType type) {
        String where = "user_id = :userId AND account_id = :accountId AND status = 'posted'"
                + (type != null ? " AND type = :type" : "");
        String typeValue = type != null ? JsonEnums.value(type) : null;
        long total = jdbc.sql("SELECT COUNT(*) FROM transactions WHERE " + where)
                .param("userId", userId).param("accountId", accountId).param("type", typeValue)
                .query(Long.class).single();
        List<Transaction> items = jdbc.sql(
                "SELECT * FROM transactions WHERE " + where + " ORDER BY date DESC, id LIMIT :limit OFFSET :offset")
                .param("userId", userId).param("accountId", accountId).param("type", typeValue)
                .param("limit", pageSize).param("offset", offset(page, pageSize))
                .query(Rows.of(Transaction.class)).list();
        return new PageResult<>(items, total);
    }

    public List<Transaction> listScheduledTransactions(String userId, String accountId) {
        return jdbc.sql("""
                SELECT * FROM transactions WHERE user_id = ? AND account_id = ? AND status = 'scheduled'
                ORDER BY date, id""")
                .params(userId, accountId).query(Rows.of(Transaction.class)).list();
    }

    private record PerformanceRow(double timeWeightedReturnPct, String currency, String benchmarkName) {
    }

    private record PointRow(String series, String period, double value) {
    }

    public Optional<PerformanceRecord> findPerformance(String userId, String accountId) {
        return jdbc.sql("SELECT * FROM account_performance WHERE user_id = ? AND account_id = ?")
                .params(userId, accountId).query(Rows.of(PerformanceRow.class)).optional()
                .map(perf -> {
                    List<PointRow> points = jdbc.sql("""
                            SELECT series, period, value FROM performance_points
                            WHERE user_id = ? AND account_id = ? ORDER BY period""")
                            .params(userId, accountId).query(Rows.of(PointRow.class)).list();
                    return new PerformanceRecord(perf.timeWeightedReturnPct(), perf.currency(), perf.benchmarkName(),
                            seriesOf(points, "account"), seriesOf(points, "benchmark"));
                });
    }

    private static List<PerformancePoint> seriesOf(List<PointRow> points, String series) {
        return points.stream()
                .filter(p -> p.series().equals(series))
                .map(p -> new PerformancePoint(p.period(), p.value()))
                .toList();
    }
}
