package com.fitflow.agent.repository;

import com.fitflow.agent.entity.WeeklyPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface WeeklyPlanRepository extends JpaRepository<WeeklyPlan, Long> {

    List<WeeklyPlan> findByMemberIdOrderByWeekStartDesc(Long memberId);

    Optional<WeeklyPlan> findFirstByMemberIdAndWeekStartOrderByCreatedAtDesc(Long memberId, LocalDate weekStart);
}
