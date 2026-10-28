package com.fitflow.booking.repository;

import com.fitflow.booking.entity.BookingReminder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BookingReminderRepository extends JpaRepository<BookingReminder, Long> {
}
