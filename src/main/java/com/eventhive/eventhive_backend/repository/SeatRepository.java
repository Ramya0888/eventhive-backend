package com.eventhive.eventhive_backend.repository;

import com.eventhive.eventhive_backend.entity.Seat;
import com.eventhive.eventhive_backend.enums.SeatStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    // All seats for an event — used for the seat map display.
    List<Seat> findByEventId(Long eventId);

    // Available seats for an event — used in booking (Module 4).
    // This is the query the idx_seats_event_status index is built for.
    List<Seat> findByEventIdAndStatus(Long eventId, SeatStatus status);

    // Count available seats — for updating event.availableSeats.
    long countByEventIdAndStatus(Long eventId, SeatStatus status);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Seat s WHERE s.id = :id")
    Optional<Seat> findByIdWithLock(@Param("id") Long id);
}