package com.fitflow.wellness.repository;

import com.fitflow.wellness.entity.HealthAccessLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HealthAccessLogRepository extends JpaRepository<HealthAccessLog, Long> {
    List<HealthAccessLog> findAllByMemberIdOrderByCreatedAtDesc(Long memberId);
}
