package com.fitflow.agent.repository;

import com.fitflow.agent.entity.JobRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JobRunRepository extends JpaRepository<JobRun, Long> {

    List<JobRun> findByNameOrderByStartedAtDesc(String name);
}
