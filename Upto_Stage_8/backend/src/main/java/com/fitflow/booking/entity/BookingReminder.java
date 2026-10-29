package com.fitflow.booking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "booking_reminders")
public class BookingReminder {

    @Id
    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
