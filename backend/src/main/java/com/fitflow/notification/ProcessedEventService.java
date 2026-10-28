package com.fitflow.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;

@Slf4j
@Service
public class ProcessedEventService {

    private final JdbcTemplate jdbcTemplate;

    public ProcessedEventService(@Qualifier("mysqlDataSource") DataSource mysqlDataSource) {
        this.jdbcTemplate = new JdbcTemplate(mysqlDataSource);
    }

    public boolean tryAcquire(String consumer, Long eventId, Instant processedAt) {
        try {
            int rows = jdbcTemplate.update(
                    "INSERT INTO processed_events (consumer, event_id, processed_at) VALUES (?, ?, ?)",
                    consumer, eventId, Timestamp.from(processedAt)
            );
            return rows > 0;
        } catch (DataIntegrityViolationException e) {
            log.debug("Event {} was already processed by consumer {}", eventId, consumer);
            return false;
        }
    }
}
