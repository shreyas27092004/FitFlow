package com.fitflow.agent.repository;

import com.fitflow.agent.entity.CoachSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface CoachSummaryRepository extends JpaRepository<CoachSummary, Long> {

    List<CoachSummary> findByMemberIdOrderByWeekStartDesc(Long memberId);

    Optional<CoachSummary> findByMemberIdAndWeekStart(Long memberId, LocalDate weekStart);
}
