package com.fitflow;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
class ConstraintIT {

    @Autowired
    @Qualifier("mysqlDataSource")
    private DataSource mysqlDataSource;

    private JdbcTemplate jdbcTemplate;

    private Long testUserId;
    private Long testClassId;
    private Long testSessionId;

    @BeforeEach
    void setUp() {
        jdbcTemplate = new JdbcTemplate(mysqlDataSource);

        // Clean up test data
        jdbcTemplate.execute("DELETE FROM waitlist_entries WHERE session_id >= 99000");
        jdbcTemplate.execute("DELETE FROM attendance_records");
        jdbcTemplate.execute("DELETE FROM trainer_session_earnings WHERE session_id >= 99000");
        jdbcTemplate.execute("DELETE FROM bookings WHERE member_id >= 99000 OR session_id >= 99000");
        jdbcTemplate.execute("DELETE FROM class_sessions WHERE id >= 99000");
        jdbcTemplate.execute("DELETE FROM fitness_classes WHERE id >= 99000");
        jdbcTemplate.execute("DELETE FROM users WHERE id >= 99000");

        testUserId = 99001L;
        testClassId = 99001L;
        testSessionId = 99001L;

        // Insert test user
        jdbcTemplate.update(
                "INSERT INTO users (id, email, password_hash, full_name, status, version, created_at) " +
                        "VALUES (?, 'constraint.user@fitflow.local', 'hash', 'Constraint Test User', 'ACTIVE', 0, UTC_TIMESTAMP())",
                testUserId
        );

        // Insert test class
        jdbcTemplate.update(
                "INSERT INTO fitness_classes (id, name, category, duration_minutes, intensity, impact_level, met_value, created_by, version, created_at) " +
                        "VALUES (?, 'Constraint Test Yoga', 'Yoga', 60, 'LOW', 'LOW', 3.0, ?, 0, UTC_TIMESTAMP())",
                testClassId, testUserId
        );

        // Insert test session
        jdbcTemplate.update(
                "INSERT INTO class_sessions (id, class_id, trainer_id, starts_at, ends_at, capacity, booked_count, room, status, trainer_rate_snapshot_paise, version, created_at) " +
                        "VALUES (?, ?, ?, UTC_TIMESTAMP(), DATE_ADD(UTC_TIMESTAMP(), INTERVAL 1 HOUR), 10, 0, 'Studio C', 'SCHEDULED', 200000, 0, UTC_TIMESTAMP())",
                testSessionId, testClassId, testUserId
        );
    }

    @Test
    @DisplayName("Active booking unique key allows only one active booking per session and member")
    void testBookingUniqueConstraintActiveFlag() {
        // First CONFIRMED booking with active_flag = 1 must succeed
        assertDoesNotThrow(() -> {
            jdbcTemplate.update(
                    "INSERT INTO bookings (id, session_id, member_id, status, consumed_from, active_flag, booked_at, version, created_at) " +
                            "VALUES (99001, ?, ?, 'CONFIRMED', 'FREE_SESSION', 1, UTC_TIMESTAMP(), 0, UTC_TIMESTAMP())",
                    testSessionId, testUserId
            );
        });

        // Duplicate CONFIRMED booking with active_flag = 1 must FAIL with DataIntegrityViolationException
        assertThrows(DataIntegrityViolationException.class, () -> {
            jdbcTemplate.update(
                    "INSERT INTO bookings (id, session_id, member_id, status, consumed_from, active_flag, booked_at, version, created_at) " +
                            "VALUES (99002, ?, ?, 'CONFIRMED', 'FREE_SESSION', 1, UTC_TIMESTAMP(), 0, UTC_TIMESTAMP())",
                    testSessionId, testUserId
            );
        });

        // Cancel first booking: set status = 'CANCELLED' and active_flag = NULL
        jdbcTemplate.update(
                "UPDATE bookings SET status = 'CANCELLED', active_flag = NULL, cancelled_at = UTC_TIMESTAMP() WHERE id = 99001"
        );

        // Re-booking with active_flag = 1 now succeeds
        assertDoesNotThrow(() -> {
            jdbcTemplate.update(
                    "INSERT INTO bookings (id, session_id, member_id, status, consumed_from, active_flag, booked_at, version, created_at) " +
                            "VALUES (99003, ?, ?, 'CONFIRMED', 'FREE_SESSION', 1, UTC_TIMESTAMP(), 0, UTC_TIMESTAMP())",
                    testSessionId, testUserId
            );
        });
    }

    @Test
    @DisplayName("CHECK constraint (booked_count >= 0 AND booked_count <= capacity) rejects overbooking")
    void testSessionBookedCountCapacityCheckConstraint() {
        // Attempting to set booked_count > capacity (11 > 10) must FAIL with CHECK constraint violation
        Exception overbookEx = assertThrows(Exception.class, () -> {
            jdbcTemplate.update(
                    "UPDATE class_sessions SET booked_count = 11 WHERE id = ?",
                    testSessionId
            );
        });
        org.junit.jupiter.api.Assertions.assertTrue(
                overbookEx.getMessage().contains("chk_sessions_booked_count") || overbookEx.getMessage().contains("Check constraint"),
                "Expected CHECK constraint violation on booked_count > capacity: " + overbookEx.getMessage()
        );

        // Attempting to set negative booked_count (-1) must FAIL with CHECK constraint violation
        Exception negativeEx = assertThrows(Exception.class, () -> {
            jdbcTemplate.update(
                    "UPDATE class_sessions SET booked_count = -1 WHERE id = ?",
                    testSessionId
            );
        });
        org.junit.jupiter.api.Assertions.assertTrue(
                negativeEx.getMessage().contains("chk_sessions_booked_count") || negativeEx.getMessage().contains("Check constraint"),
                "Expected CHECK constraint violation on booked_count < 0: " + negativeEx.getMessage()
        );

        // Setting booked_count within bounds (10 <= 10) must succeed
        assertDoesNotThrow(() -> {
            jdbcTemplate.update(
                    "UPDATE class_sessions SET booked_count = 10 WHERE id = ?",
                    testSessionId
            );
        });
    }
}
