package com.fitflow.booking.repository;

import com.fitflow.booking.entity.AttendanceRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {

    Optional<AttendanceRecord> findByBookingId(Long bookingId);

    List<AttendanceRecord> findByBookingIdIn(Collection<Long> bookingIds);

    boolean existsByBookingIdIn(Collection<Long> bookingIds);
}
