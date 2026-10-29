package com.fitflow.trainer.repository;

import com.fitflow.trainer.entity.TrainerProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TrainerProfileRepository extends JpaRepository<TrainerProfile, Long> {
    List<TrainerProfile> findByActiveTrue();
}
