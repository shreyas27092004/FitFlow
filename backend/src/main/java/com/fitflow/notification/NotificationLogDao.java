package com.fitflow.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;

@Slf4j
@Repository
public class NotificationLogDao {

    private final JdbcTemplate jdbcTemplate;

    public NotificationLogDao(@Qualifier("postgresDataSource") DataSource postgresDataSource) {
        this.jdbcTemplate = new JdbcTemplate(postgresDataSource);
    }

    public boolean hasAlreadySent(Long userId, String template, Long eventId) {
        if (eventId == null) {
            return false;
        }
        String sql = "SELECT COUNT(*) FROM notification_log WHERE user_id = ? AND template = ? AND event_id = ? AND status = 'SENT'";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, userId, template, eventId);
        return count != null && count > 0;
    }

    public Long recordPending(Long userId, String channel, String template, Long eventId, Instant createdAt) {
        String sql = "INSERT INTO notification_log (user_id, channel, template, status, event_id, created_at, updated_at) " +
                "VALUES (?, ?, ?, 'PENDING', ?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, userId);
            ps.setString(2, channel);
            ps.setString(3, template);
            if (eventId != null) {
                ps.setLong(4, eventId);
            } else {
                ps.setNull(4, java.sql.Types.BIGINT);
            }
            ps.setTimestamp(5, Timestamp.from(createdAt));
            ps.setTimestamp(6, Timestamp.from(createdAt));
            return ps;
        }, keyHolder);

        Map<String, Object> keys = keyHolder.getKeys();
        if (keys != null && keys.containsKey("id")) {
            Object idVal = keys.get("id");
            if (idVal instanceof Number n) {
                return n.longValue();
            }
        }
        try {
            Number key = keyHolder.getKey();
            return key != null ? key.longValue() : null;
        } catch (Exception e) {
            return null;
        }
    }

    public void recordSuccess(Long logId, Instant updatedAt) {
        if (logId == null) {
            return;
        }
        String sql = "UPDATE notification_log SET status = 'SENT', updated_at = ? WHERE id = ?";
        jdbcTemplate.update(sql, Timestamp.from(updatedAt), logId);
    }

    public void recordFailure(Long logId, String error, Instant updatedAt) {
        if (logId == null) {
            return;
        }
        String shortError = error != null && error.length() > 500 ? error.substring(0, 500) : error;
        String sql = "UPDATE notification_log SET status = 'FAILED', error = ?, updated_at = ? WHERE id = ?";
        jdbcTemplate.update(sql, shortError, Timestamp.from(updatedAt), logId);
    }
}
