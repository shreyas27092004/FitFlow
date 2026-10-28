package com.fitflow.wellness.repository;

import com.fitflow.wellness.entity.HealthConsent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HealthConsentRepository extends JpaRepository<HealthConsent, Long> {

    List<HealthConsent> findAllByMemberIdOrderByCreatedAtDesc(Long memberId);

    List<HealthConsent> findAllByMemberIdAndRevokedAtIsNull(Long memberId);

    Optional<HealthConsent> findFirstByMemberIdAndPurposeAndRevokedAtIsNullOrderByGrantedAtDesc(Long memberId, String purpose);

    @Query("SELECT COUNT(c) > 0 FROM HealthConsent c WHERE c.memberId = :memberId AND c.purpose = :purpose AND c.revokedAt IS NULL")
    boolean hasActiveConsent(@Param("memberId") Long memberId, @Param("purpose") String purpose);
}
