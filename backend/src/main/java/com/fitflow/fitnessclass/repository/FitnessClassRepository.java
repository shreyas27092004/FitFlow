package com.fitflow.fitnessclass.repository;

import com.fitflow.fitnessclass.entity.FitnessClass;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FitnessClassRepository extends JpaRepository<FitnessClass, Long> {
    Optional<FitnessClass> findByName(String name);
    boolean existsByName(String name);
}
