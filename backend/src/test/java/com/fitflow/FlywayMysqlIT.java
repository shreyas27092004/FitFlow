package com.fitflow;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class FlywayMysqlIT {

    @Autowired
    @Qualifier("mysqlDataSource")
    private DataSource mysqlDataSource;

    @Autowired
    @Qualifier("mysqlFlyway")
    private Flyway mysqlFlyway;

    private static final List<String> EXPECTED_MYSQL_TABLES = List.of(
            "users",
            "user_roles",
            "admin_permissions",
            "refresh_tokens",
            "password_reset_tokens",
            "trainer_profiles",
            "fitness_classes",
            "class_sessions",
            "bookings",
            "waitlist_entries",
            "attendance_records",
            "membership_plans",
            "memberships",
            "membership_changes",
            "payments",
            "refunds",
            "invoices",
            "trainer_payouts",
            "trainer_session_earnings",
            "outbox_events",
            "member_health_profiles",
            "health_consents",
            "body_metrics",
            "progress_logs",
            "class_feedback",
            "admin_action_log",
            "app_secrets",
            "app_settings",
            "idempotency_records",
            "pending_membership_changes",
            "refund_allocations",
            "payment_webhook_events",
            "job_locks",
            "processed_events",
            "booking_reminders",
            "membership_notices"
    );

    @Test
    @DisplayName("Assert all required MySQL core tables exist after migration")
    void assertAllKeyMysqlTablesExist() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(mysqlDataSource);
        List<String> actualTables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()",
                String.class
        ).stream().map(String::toLowerCase).toList();

        for (String expectedTable : EXPECTED_MYSQL_TABLES) {
            assertTrue(actualTables.contains(expectedTable.toLowerCase()),
                    "Expected table " + expectedTable + " was not found in MySQL test database");
        }
    }

    @Test
    @DisplayName("Assert re-running MySQL Flyway migration is a no-op")
    void assertReRunningFlywayIsNoOp() {
        MigrateResult result = mysqlFlyway.migrate();
        assertEquals(0, result.migrationsExecuted,
                "Re-running Flyway on an already migrated database must execute 0 migrations");
        assertTrue(result.success, "Migration status must be success");
    }
}
