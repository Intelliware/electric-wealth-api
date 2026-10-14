package com.electricwealth.api.repository;

import static com.electricwealth.api.repository.SqlValues.isoNow;
import static com.electricwealth.api.repository.SqlValues.isoTimestamp;
import static com.electricwealth.api.repository.SqlValues.toInt;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sessions live in the shared {@code sessions} table, so a token issued by the Node API also works here
 * (and the other way round).
 */
@Repository
public class SessionRepository {

    public static final int TOKEN_TTL_SECONDS = 900;

    public record SessionRecord(String accessToken, String refreshToken, String userId, boolean mfaVerified,
            boolean revoked, String expiresAt) {

        boolean isActive() {
            return !revoked && Instant.parse(expiresAt).isAfter(Instant.now());
        }
    }

    private final JdbcClient jdbc;

    public SessionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private static String expiryFromNow() {
        return isoTimestamp(Instant.now().plusSeconds(TOKEN_TTL_SECONDS));
    }

    public SessionRecord create(String userId, boolean mfaVerified) {
        var session = new SessionRecord("mock-at-" + UUID.randomUUID(), "mock-rt-" + UUID.randomUUID(), userId,
                mfaVerified, false, expiryFromNow());
        jdbc.sql("""
                INSERT INTO sessions (access_token, refresh_token, user_id, mfa_verified, expires_at, created_at)
                VALUES (?, ?, ?, ?, ?, ?)""")
                .params(session.accessToken(), session.refreshToken(), userId, toInt(mfaVerified),
                        session.expiresAt(), isoNow())
                .update();
        return session;
    }

    /** Returns the session for an access token if it exists, is not revoked and has not expired. */
    public Optional<SessionRecord> findActive(String accessToken) {
        return jdbc.sql("SELECT * FROM sessions WHERE access_token = ?")
                .param(accessToken)
                .query(SessionRecord.class)
                .optional()
                .filter(SessionRecord::isActive);
    }

    public Optional<SessionRecord> markMfaVerified(String accessToken) {
        jdbc.sql("UPDATE sessions SET mfa_verified = 1, expires_at = ? WHERE access_token = ? AND revoked = 0")
                .params(expiryFromNow(), accessToken)
                .update();
        return findActive(accessToken);
    }

    /** Exchanges a refresh token for a new session, revoking the old one (refresh-token rotation). */
    @Transactional
    public Optional<SessionRecord> rotate(String refreshToken) {
        Optional<SessionRecord> old = jdbc.sql("SELECT * FROM sessions WHERE refresh_token = ? AND revoked = 0")
                .param(refreshToken)
                .query(SessionRecord.class)
                .optional();
        old.ifPresent(s -> jdbc.sql("UPDATE sessions SET revoked = 1 WHERE access_token = ?")
                .param(s.accessToken())
                .update());
        return old.map(s -> create(s.userId(), s.mfaVerified()));
    }

    public void revoke(@Nullable String accessToken, @Nullable String refreshToken) {
        if (accessToken != null) {
            jdbc.sql("UPDATE sessions SET revoked = 1 WHERE access_token = ?").param(accessToken).update();
        }
        if (refreshToken != null) {
            jdbc.sql("UPDATE sessions SET revoked = 1 WHERE refresh_token = ?").param(refreshToken).update();
        }
    }
}
