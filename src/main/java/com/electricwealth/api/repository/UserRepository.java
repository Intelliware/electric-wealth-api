package com.electricwealth.api.repository;

import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {

    public record UserRecord(String id, String email, String password, String mfaCode, String name) {
    }

    private static final String SELECT = "SELECT id, email, password, mfa_code, name FROM users ";

    private final JdbcClient jdbc;

    public UserRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<UserRecord> findByEmail(String email) {
        return jdbc.sql(SELECT + "WHERE email = ?").param(email).query(UserRecord.class).optional();
    }

    public Optional<UserRecord> findById(String id) {
        return jdbc.sql(SELECT + "WHERE id = ?").param(id).query(UserRecord.class).optional();
    }
}
