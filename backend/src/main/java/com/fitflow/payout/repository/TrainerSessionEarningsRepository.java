package com.fitflow.payout.repository;

import com.fitflow.payout.entity.TrainerSessionEarnings;
import com.fitflow.payout.model.EarningsStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface TrainerSessionEarningsRepository extends JpaRepository<TrainerSessionEarnings, Long> {

    Optional<TrainerSessionEarnings> findBySessionId(Long sessionId);

    boolean existsBySessionId(Long sessionId);

    List<TrainerSessionEarnings> findByTrainerId(Long trainerId);

    List<TrainerSessionEarnings> findByTrainerIdAndCreatedAtBetween(Long trainerId, Instant from, Instant to);

    List<TrainerSessionEarnings> findByStatusAndCreatedAtBetween(EarningsStatus status, Instant from, Instant to);

    List<TrainerSessionEarnings> findByPayoutId(Long payoutId);

    @Query("SELECT COALESCE(SUM(e.amountPaise), 0) FROM TrainerSessionEarnings e WHERE e.trainerId = :trainerId")
    long sumTotalEarningsForTrainer(@Param("trainerId") Long trainerId);

    @Query("SELECT COALESCE(SUM(e.amountPaise), 0) FROM TrainerSessionEarnings e WHERE e.trainerId = :trainerId AND e.status = :status")
    long sumEarningsForTrainerByStatus(@Param("trainerId") Long trainerId, @Param("status") EarningsStatus status);
}
