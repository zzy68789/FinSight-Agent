package com.zzy.finsight.repository;

import com.zzy.finsight.domain.AppUserRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public class AppUserRepository {
    private static final RowMapper<AppUserRecord> USER_MAPPER = (rs, rowNum) -> new AppUserRecord(
            rs.getLong("id"),
            rs.getString("username"),
            rs.getString("email"),
            rs.getString("password_hash"),
            rs.getString("role"),
            rs.getString("status"),
            rs.getObject("created_at", LocalDateTime.class),
            rs.getObject("updated_at", LocalDateTime.class),
            rs.getObject("last_login_at", LocalDateTime.class)
    );

    private final JdbcTemplate jdbcTemplate;

    public AppUserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public AppUserRecord create(String username, String email, String passwordHash, String role) {
        LocalDateTime now = LocalDateTime.now();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO app_user(username, email, password_hash, role, status, created_at, updated_at)
                    VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, username);
            statement.setString(2, email);
            statement.setString(3, passwordHash);
            statement.setString(4, role);
            statement.setObject(5, now);
            statement.setObject(6, now);
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("创建用户后未返回主键");
        }
        return new AppUserRecord(key.longValue(), username, email, passwordHash, role, "ACTIVE", now, now, null);
    }

    public Optional<AppUserRecord> findByUsername(String username) {
        return jdbcTemplate.query("""
                        SELECT id, username, email, password_hash, role, status, created_at, updated_at, last_login_at
                        FROM app_user
                        WHERE username = ?
                        """,
                USER_MAPPER,
                username
        ).stream().findFirst();
    }

    public Optional<AppUserRecord> findById(long userId) {
        return jdbcTemplate.query("""
                        SELECT id, username, email, password_hash, role, status, created_at, updated_at, last_login_at
                        FROM app_user
                        WHERE id = ?
                        """,
                USER_MAPPER,
                userId
        ).stream().findFirst();
    }

    public void updateLastLoginAt(long userId) {
        jdbcTemplate.update(
                "UPDATE app_user SET last_login_at = ?, updated_at = ? WHERE id = ?",
                LocalDateTime.now(),
                LocalDateTime.now(),
                userId
        );
    }
}
