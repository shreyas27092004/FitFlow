package com.fitflow.common.job;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface JobLockRepository extends JpaRepository<JobLock, String> {

    @Modifying
    @Query(value = "INSERT INTO job_locks (job_name, locked_at, locked_by) VALUES (:jobName, :lockedAt, :lockedBy) " +
            "ON DUPLICATE KEY UPDATE locked_at = IF(locked_at < :expiredBefore, :lockedAt, locked_at), " +
            "locked_by = IF(locked_at < :expiredBefore, :lockedBy, locked_by)", nativeQuery = true)
    int acquireLock(
            @Param("jobName") String jobName,
            @Param("lockedAt") Instant lockedAt,
            @Param("lockedBy") String lockedBy,
            @Param("expiredBefore") Instant expiredBefore
    );

    @Modifying
    @Query("DELETE FROM JobLock j WHERE j.jobName = :jobName AND j.lockedBy = :lockedBy")
    int releaseLock(@Param("jobName") String jobName, @Param("lockedBy") String lockedBy);
}
