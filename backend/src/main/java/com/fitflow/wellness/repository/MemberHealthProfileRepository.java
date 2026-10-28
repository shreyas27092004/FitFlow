package com.fitflow.wellness.repository;

import com.fitflow.wellness.entity.MemberHealthProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MemberHealthProfileRepository extends JpaRepository<MemberHealthProfile, Long> {
    Optional<MemberHealthProfile> findByMemberId(Long memberId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM MemberHealthProfile m WHERE m.memberId = :memberId")
    void deleteByMemberId(@Param("memberId") Long memberId);
}
