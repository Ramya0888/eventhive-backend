package com.eventhive.eventhive_backend.repository;

import com.eventhive.eventhive_backend.entity.Booking;
import com.eventhive.eventhive_backend.enums.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    // Attendee's booking history.
    List<Booking> findByUserIdOrderByCreatedAtDesc(Long userId);

    // Lookup by human-readable reference code.
    Optional<Booking> findByBookingReference(String bookingReference);

    /**
     * Find expired PENDING bookings for the cleanup job.
     * These are bookings where payment wasn't completed in time —
     * their seats need to be released back to AVAILABLE.
     */
    @Query("""
            SELECT b FROM Booking b
            WHERE b.status = :status
              AND b.expiresAt < :now
            """)
    List<Booking> findExpiredBookings(
            @Param("status") BookingStatus status,
            @Param("now") LocalDateTime now);
}