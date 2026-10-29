package com.fitflow.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Slf4j
@Repository
public class NotificationRecipientDao {

    public record RecipientInfo(
            Long id,
            String email,
            String firstName,
            String status,
            boolean emailBookings,
            boolean emailReminders,
            boolean emailMarketing
    ) {}

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public NotificationRecipientDao(
            @Qualifier("mysqlDataSource") DataSource mysqlDataSource,
            ObjectMapper objectMapper) {
        this.jdbcTemplate = new JdbcTemplate(mysqlDataSource);
        this.objectMapper = objectMapper;
    }

    public Optional<RecipientInfo> findById(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        String sql = "SELECT id, email, full_name, status, notification_prefs FROM users WHERE id = ?";
        try {
            RecipientInfo info = jdbcTemplate.queryForObject(sql, (rs, rowNum) -> {
                Long id = rs.getLong("id");
                String email = rs.getString("email");
                String fullName = rs.getString("full_name");
                String status = rs.getString("status");
                String prefsJson = rs.getString("notification_prefs");

                boolean emailBookings = true;
                boolean emailReminders = true;
                boolean emailMarketing = false;

                if (prefsJson != null && !prefsJson.isBlank()) {
                    try {
                        JsonNode node = objectMapper.readTree(prefsJson);
                        if (node.has("emailBookings")) {
                            emailBookings = node.get("emailBookings").asBoolean(true);
                        }
                        if (node.has("emailReminders")) {
                            emailReminders = node.get("emailReminders").asBoolean(true);
                        }
                        if (node.has("emailMarketing")) {
                            emailMarketing = node.get("emailMarketing").asBoolean(false);
                        }
                    } catch (Exception e) {
                        log.warn("Failed to parse notification_prefs for user id {}: {}", id, e.getMessage());
                    }
                }

                String safeFirstName = (fullName != null && !fullName.isBlank()) ? fullName.trim().split("\\s+")[0] : "Member";
                return new RecipientInfo(id, email, safeFirstName, status, emailBookings, emailReminders, emailMarketing);
            }, userId);
            return Optional.ofNullable(info);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public List<RecipientInfo> findAdminsWithPermission(String permission) {
        String sql = """
            SELECT DISTINCT u.id, u.email, u.full_name, u.status, u.notification_prefs
            FROM users u
            JOIN user_roles ur ON u.id = ur.user_id AND ur.role = 'ADMIN'
            JOIN admin_permissions ap ON u.id = ap.user_id AND (ap.permission = ? OR ap.permission = 'SUPER_ADMIN')
            WHERE u.status = 'ACTIVE'
        """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            Long id = rs.getLong("id");
            String email = rs.getString("email");
            String fullName = rs.getString("full_name");
            String status = rs.getString("status");
            String prefsJson = rs.getString("notification_prefs");

            boolean emailBookings = true;
            boolean emailReminders = true;
            boolean emailMarketing = false;

            if (prefsJson != null && !prefsJson.isBlank()) {
                try {
                    JsonNode node = objectMapper.readTree(prefsJson);
                    if (node.has("emailBookings")) {
                        emailBookings = node.get("emailBookings").asBoolean(true);
                    }
                    if (node.has("emailReminders")) {
                        emailReminders = node.get("emailReminders").asBoolean(true);
                    }
                    if (node.has("emailMarketing")) {
                        emailMarketing = node.get("emailMarketing").asBoolean(false);
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse notification_prefs for admin id {}: {}", id, e.getMessage());
                }
            }

            String safeFirstName = (fullName != null && !fullName.isBlank()) ? fullName.trim().split("\\s+")[0] : "Admin";
            return new RecipientInfo(id, email, safeFirstName, status, emailBookings, emailReminders, emailMarketing);
        }, permission);
    }

    public List<RecipientInfo> findActiveMembers() {
        String sql = """
            SELECT DISTINCT u.id, u.email, u.full_name, u.status, u.notification_prefs
            FROM users u
            JOIN user_roles ur ON u.id = ur.user_id AND ur.role = 'MEMBER'
            WHERE u.status = 'ACTIVE'
        """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            Long id = rs.getLong("id");
            String email = rs.getString("email");
            String fullName = rs.getString("full_name");
            String status = rs.getString("status");
            String prefsJson = rs.getString("notification_prefs");

            boolean emailBookings = true;
            boolean emailReminders = true;
            boolean emailMarketing = false;

            if (prefsJson != null && !prefsJson.isBlank()) {
                try {
                    JsonNode node = objectMapper.readTree(prefsJson);
                    if (node.has("emailBookings")) {
                        emailBookings = node.get("emailBookings").asBoolean(true);
                    }
                    if (node.has("emailReminders")) {
                        emailReminders = node.get("emailReminders").asBoolean(true);
                    }
                    if (node.has("emailMarketing")) {
                        emailMarketing = node.get("emailMarketing").asBoolean(false);
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse notification_prefs for member id {}: {}", id, e.getMessage());
                }
            }

            String safeFirstName = (fullName != null && !fullName.isBlank()) ? fullName.trim().split("\\s+")[0] : "Member";
            return new RecipientInfo(id, email, safeFirstName, status, emailBookings, emailReminders, emailMarketing);
        });
    }

    public List<Long> findConfirmedBookingUserIdsForSession(Long sessionId) {
        if (sessionId == null) {
            return Collections.emptyList();
        }
        String sql = "SELECT member_id FROM bookings WHERE session_id = ? AND status = 'CONFIRMED'";
        return jdbcTemplate.query(sql, (rs, rowNum) -> rs.getLong("member_id"), sessionId);
    }

    public Optional<Long> findTrainerUserIdForSession(Long sessionId) {
        if (sessionId == null) {
            return Optional.empty();
        }
        String sql = """
            SELECT tp.user_id
            FROM class_sessions cs
            JOIN trainer_profiles tp ON cs.trainer_id = tp.id
            WHERE cs.id = ?
        """;
        try {
            Long userId = jdbcTemplate.queryForObject(sql, Long.class, sessionId);
            return Optional.ofNullable(userId);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
}
