package com.fitflow.wellness.repository;

import com.fitflow.wellness.entity.RestrictionProposal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RestrictionProposalRepository extends JpaRepository<RestrictionProposal, Long> {
    List<RestrictionProposal> findAllByMemberIdAndStatusOrderByCreatedAtDesc(Long memberId, String status);
    Optional<RestrictionProposal> findByIdAndMemberId(Long id, Long memberId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM RestrictionProposal r WHERE r.memberId = :memberId")
    void deleteAllByMemberId(@Param("memberId") Long memberId);
}
