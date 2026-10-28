package com.fitflow.agent.repository;

import com.fitflow.agent.entity.AgentUsage;
import com.fitflow.agent.entity.AgentUsageId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface AgentUsageRepository extends JpaRepository<AgentUsage, AgentUsageId> {

    @Query("SELECT u FROM AgentUsage u WHERE u.id.userId = :userId AND u.id.day = :day")
    Optional<AgentUsage> findByUserIdAndDay(@Param("userId") Long userId, @Param("day") LocalDate day);

    @Modifying
    @Transactional("postgresTransactionManager")
    @Query(value = "INSERT INTO agent_usage (user_id, day, calls) VALUES (:userId, :day, 1) " +
            "ON CONFLICT (user_id, day) DO UPDATE SET calls = agent_usage.calls + 1",
            nativeQuery = true)
    void incrementUsage(@Param("userId") Long userId, @Param("day") LocalDate day);
}
