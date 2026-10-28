package com.fitflow.session.repository;

import com.fitflow.session.entity.ClassSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface ClassSessionRepository extends JpaRepository<ClassSession, Long>, JpaSpecificationExecutor<ClassSession> {

    @Query("""
        SELECT s FROM ClassSession s
        WHERE s.trainerId = :trainerId
          AND s.status != 'CANCELLED'
          AND s.startsAt < :endsAt
          AND s.endsAt > :startsAt
          AND (:excludeId IS NULL OR s.id != :excludeId)
    """)
    List<ClassSession> findOverlappingTrainerSessions(
            @Param("trainerId") Long trainerId,
            @Param("startsAt") Instant startsAt,
            @Param("endsAt") Instant endsAt,
            @Param("excludeId") Long excludeId
    );

    @Query("""
        SELECT s FROM ClassSession s
        WHERE s.room = :room
          AND s.status != 'CANCELLED'
          AND s.startsAt < :endsAt
          AND s.endsAt > :startsAt
          AND (:excludeId IS NULL OR s.id != :excludeId)
    """)
    List<ClassSession> findOverlappingRoomSessions(
            @Param("room") String room,
            @Param("startsAt") Instant startsAt,
            @Param("endsAt") Instant endsAt,
            @Param("excludeId") Long excludeId
    );

    List<ClassSession> findByTrainerIdAndStartsAtBetweenOrderByStartsAtAsc(
            Long trainerId,
            Instant from,
            Instant to
    );

    List<ClassSession> findByStartsAtBetween(Instant from, Instant to);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE ClassSession s
        SET s.bookedCount = s.bookedCount + 1
        WHERE s.id = :sessionId
          AND s.bookedCount < s.capacity
          AND s.status = 'SCHEDULED'
    """)
    int allocateSeatAtomic(@Param("sessionId") Long sessionId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE ClassSession s
        SET s.bookedCount = s.bookedCount - 1
        WHERE s.id = :sessionId
          AND s.bookedCount > 0
    """)
    int releaseSeatAtomic(@Param("sessionId") Long sessionId);
}
