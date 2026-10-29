package com.fitflow.membership.repository;

import com.fitflow.membership.entity.FreeSessionGrant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface FreeSessionGrantRepository extends JpaRepository<FreeSessionGrant, Long> {

    @Query("SELECT COALESCE(SUM(g.sessions), 0) FROM FreeSessionGrant g WHERE g.memberId = :memberId AND g.createdAt >= :since")
    int countSessionsGrantedSince(@Param("memberId") Long memberId, @Param("since") Instant since);
}
