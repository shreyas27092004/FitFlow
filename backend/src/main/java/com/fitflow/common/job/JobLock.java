package com.fitflow.common.job;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "job_locks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobLock {

    @Id
    @Column(name = "job_name", nullable = false, length = 50)
    private String jobName;

    @Column(name = "locked_at", nullable = false)
    private Instant lockedAt;

    @Column(name = "locked_by", nullable = false, length = 100)
    private String lockedBy;
}
