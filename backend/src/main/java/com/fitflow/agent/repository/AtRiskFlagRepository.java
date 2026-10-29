package com.fitflow.agent.repository;

import com.fitflow.agent.entity.AtRiskFlag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

@Repository
public interface AtRiskFlagRepository extends JpaRepository<AtRiskFlag, Long> {

    @Query("SELECT COUNT(f) > 0 FROM AtRiskFlag f WHERE f.memberId = :memberId AND f.flaggedOn >= :since")
    boolean wasFlaggedSince(@Param("memberId") Long memberId, @Param("since") LocalDate since);
}
