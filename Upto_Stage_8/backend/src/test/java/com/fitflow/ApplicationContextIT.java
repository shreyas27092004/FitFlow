package com.fitflow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class ApplicationContextIT {

    @Test
    @DisplayName("Spring ApplicationContext loads successfully with test profile")
    void contextLoads() {
        assertTrue(true, "Application context initialized dual datasources, JPA, and security beans without errors");
    }
}
