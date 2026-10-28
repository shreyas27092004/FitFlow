package com.fitflow.wellness.repository;

import com.fitflow.wellness.entity.ProgressLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProgressLogRepository extends JpaRepository<ProgressLog, Long> {
    List<ProgressLog> findAllByMemberIdOrderByLogDateDesc(Long memberId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM ProgressLog p WHERE p.memberId = :memberId")
    void deleteAllByMemberId(@Param("memberId") Long memberId);
}
