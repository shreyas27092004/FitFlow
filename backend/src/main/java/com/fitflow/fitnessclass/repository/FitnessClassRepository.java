package com.fitflow.fitnessclass.repository;

import com.fitflow.fitnessclass.entity.FitnessClass;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FitnessClassRepository extends JpaRepository<FitnessClass, Long>, JpaSpecificationExecutor<FitnessClass> {
    Optional<FitnessClass> findByName(String name);
    boolean existsByName(String name);
    boolean existsByNameAndIdNot(String name, Long id);
}
