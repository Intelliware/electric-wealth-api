package com.electricwealth.api.repository;

import java.util.Optional;

import com.electricwealth.api.dto.DashboardDtos.AllocationItem;
import com.electricwealth.api.dto.DashboardDtos.DashboardAlert;
import com.electricwealth.api.dto.DashboardDtos.DashboardGoalSummary;
import com.electricwealth.api.dto.DashboardDtos.DashboardResponse;
import com.electricwealth.api.dto.DashboardDtos.PortfolioSummary;
import com.electricwealth.api.dto.DashboardDtos.QuickLink;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class DashboardRepository {

    private record SnapshotRow(String currency, long dayChange, double dayChangePct, String asOf) {
    }

    private final JdbcClient jdbc;
    private final AccountRepository accounts;

    public DashboardRepository(JdbcClient jdbc, AccountRepository accounts) {
        this.jdbc = jdbc;
        this.accounts = accounts;
    }

    /** Assembles the dashboard from several tables. Empty if the user has no portfolio snapshot. */
    public Optional<DashboardResponse> find(String userId) {
        return jdbc.sql("SELECT currency, day_change, day_change_pct, as_of FROM portfolio_snapshots WHERE user_id = ?")
                .param(userId).query(Rows.of(SnapshotRow.class)).optional()
                .map(snapshot -> new DashboardResponse(
                        new PortfolioSummary(accounts.totalVisibleBalance(userId), snapshot.currency(),
                                snapshot.dayChange(), snapshot.dayChangePct(), snapshot.asOf()),
                        jdbc.sql("SELECT asset_class, pct FROM asset_allocations WHERE user_id = ? ORDER BY sort_order")
                                .param(userId).query(Rows.of(AllocationItem.class)).list(),
                        jdbc.sql("""
                                SELECT id, name, progress_pct FROM goals
                                WHERE user_id = ? AND show_on_dashboard = 1 ORDER BY sort_order""")
                                .param(userId).query(Rows.of(DashboardGoalSummary.class)).list(),
                        jdbc.sql("SELECT id, type, title, deep_link FROM alerts WHERE user_id = ? ORDER BY id")
                                .param(userId).query(Rows.of(DashboardAlert.class)).list(),
                        jdbc.sql("SELECT label, deep_link FROM quick_links WHERE user_id = ? ORDER BY sort_order")
                                .param(userId).query(Rows.of(QuickLink.class)).list()));
    }
}
