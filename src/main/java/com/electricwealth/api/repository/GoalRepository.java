package com.electricwealth.api.repository;

import static com.electricwealth.api.repository.SqlValues.offset;

import java.util.List;
import java.util.UUID;

import com.electricwealth.api.config.JsonEnums;
import com.electricwealth.api.dto.GoalDtos.Goal;
import com.electricwealth.api.dto.GoalDtos.GoalCreateRequest;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class GoalRepository {

    private final JdbcClient jdbc;

    public GoalRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public PageResult<Goal> list(String userId, int page, int pageSize) {
        long total = jdbc.sql("SELECT COUNT(*) FROM goals WHERE user_id = ?").param(userId).query(Long.class).single();
        List<Goal> items = jdbc.sql("SELECT * FROM goals WHERE user_id = ? ORDER BY sort_order, id LIMIT ? OFFSET ?")
                .params(userId, pageSize, offset(page, pageSize))
                .query(Rows.of(Goal.class)).list();
        return new PageResult<>(items, total);
    }

    /** Creates a goal at the end of the user's list. New goals are not pinned to the dashboard. */
    @Transactional
    public Goal create(String userId, GoalCreateRequest input) {
        String id = "goal-" + UUID.randomUUID().toString().substring(0, 8);
        long current = input.currentAmount() != null ? input.currentAmount() : 0;
        long progressPct = Math.round((double) current / input.targetAmount() * 100);
        int next = jdbc.sql("SELECT COALESCE(MAX(sort_order), 0) + 1 FROM goals WHERE user_id = ?")
                .param(userId).query(Integer.class).single();
        String targetDate = input.targetDate().toString();
        jdbc.sql("""
                INSERT INTO goals (user_id, id, name, category, current_amount, target_amount, currency, target_date,
                                   progress_pct, show_on_dashboard, sort_order)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?)""")
                .params(userId, id, input.name(), JsonEnums.value(input.category()), current, input.targetAmount(),
                        input.currency(), targetDate, progressPct, next)
                .update();
        return new Goal(id, input.name(), input.category(), current, input.targetAmount(), input.currency(),
                targetDate, progressPct);
    }
}
