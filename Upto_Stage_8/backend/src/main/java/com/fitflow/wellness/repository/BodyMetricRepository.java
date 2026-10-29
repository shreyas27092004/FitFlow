package com.fitflow.wellness.repository;

import com.fitflow.wellness.entity.BodyMetric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface BodyMetricRepository extends JpaRepository<BodyMetric, Long> {

    List<BodyMetric> findAllByMemberIdOrderByRecordedOnDesc(Long memberId);

    List<BodyMetric> findAllByMemberIdAndRecordedOnBetweenOrderByRecordedOnDesc(
            Long memberId, LocalDate from, LocalDate to);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM BodyMetric b WHERE b.memberId = :memberId")
    void deleteAllByMemberId(@Param("memberId") Long memberId);
}
