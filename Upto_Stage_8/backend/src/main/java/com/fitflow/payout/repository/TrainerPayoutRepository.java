package com.fitflow.payout.repository;

import com.fitflow.payout.entity.TrainerPayout;
import com.fitflow.payout.model.PayoutStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TrainerPayoutRepository extends JpaRepository<TrainerPayout, Long> {

    List<TrainerPayout> findByStatus(PayoutStatus status);
}
