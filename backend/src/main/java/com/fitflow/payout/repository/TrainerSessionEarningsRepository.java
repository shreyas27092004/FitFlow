package com.fitflow.payout.repository;

import com.fitflow.payout.entity.TrainerSessionEarnings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TrainerSessionEarningsRepository extends JpaRepository<TrainerSessionEarnings, Long> {

    Optional<TrainerSessionEarnings> findBySessionId(Long sessionId);

    boolean existsBySessionId(Long sessionId);

    List<TrainerSessionEarnings> findByTrainerId(Long trainerId);
}
