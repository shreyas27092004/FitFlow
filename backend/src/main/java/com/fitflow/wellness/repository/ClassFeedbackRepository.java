package com.fitflow.wellness.repository;

import com.fitflow.wellness.entity.ClassFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClassFeedbackRepository extends JpaRepository<ClassFeedback, Long> {
    Optional<ClassFeedback> findByBookingId(Long bookingId);
    List<ClassFeedback> findAllByMemberIdOrderByCreatedAtDesc(Long memberId);
}
