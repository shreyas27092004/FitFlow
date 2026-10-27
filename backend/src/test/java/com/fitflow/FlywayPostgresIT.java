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
class FlywayPostgresIT {

    @Autowired
    @Qualifier("postgresDataSource")
    private DataSource postgresDataSource;

    @Autowired
    @Qualifier("postgresFlyway")
    private Flyway postgresFlyway;

    private static final List<String> EXPECTED_POSTGRES_TABLES = List.of(
            "agent_conversations",
            "agent_messages",
            "agent_memory",
            "agent_action_audit",
            "pending_actions",
            "nutrition_plans",
            "weekly_plans",
            "coach_summaries",
            "notification_log"
    );

    @Test
    @DisplayName("Assert all required PostgreSQL agent tables exist after migration")
    void assertAllKeyPostgresTablesExist() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(postgresDataSource);
        List<String> actualTables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
                String.class
        ).stream().map(String::toLowerCase).toList();

        for (String expectedTable : EXPECTED_POSTGRES_TABLES) {
            assertTrue(actualTables.contains(expectedTable.toLowerCase()),
                    "Expected table " + expectedTable + " was not found in PostgreSQL test database");
        }
    }

    @Test
    @DisplayName("Assert re-running PostgreSQL Flyway migration is a no-op")
    void assertReRunningFlywayIsNoOp() {
        MigrateResult result = postgresFlyway.migrate();
        assertEquals(0, result.migrationsExecuted,
                "Re-running Flyway on an already migrated PostgreSQL database must execute 0 migrations");
        assertTrue(result.success, "Migration status must be success");
    }
}
