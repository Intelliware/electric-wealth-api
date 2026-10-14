package com.electricwealth.api.repository;

import java.util.List;

import com.electricwealth.api.dto.NotificationDtos.Notification;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class NotificationRepository {

    private final JdbcClient jdbc;

    public NotificationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<Notification> list(String userId) {
        return jdbc.sql("SELECT * FROM notifications WHERE user_id = ? ORDER BY created_at DESC, id")
                .param(userId).query(Rows.of(Notification.class)).list();
    }

    /** Marks one notification read. Returns false if the user has no notification with that id. */
    public boolean markRead(String userId, String id) {
        return jdbc.sql("UPDATE notifications SET read = 1 WHERE user_id = ? AND id = ?").params(userId, id).update() > 0;
    }

    /** Marks all of the user's notifications read. Returns how many were unread. */
    public int markAllRead(String userId) {
        return jdbc.sql("UPDATE notifications SET read = 1 WHERE user_id = ? AND read = 0").param(userId).update();
    }
}
