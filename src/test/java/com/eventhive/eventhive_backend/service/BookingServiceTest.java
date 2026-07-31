package com.eventhive.eventhive_backend.service;

import com.eventhive.eventhive_backend.dto.BookingResponse;
import com.eventhive.eventhive_backend.dto.CreateBookingRequest;
import com.eventhive.eventhive_backend.entity.*;
import com.eventhive.eventhive_backend.enums.BookingStatus;
import com.eventhive.eventhive_backend.enums.EventStatus;
import com.eventhive.eventhive_backend.enums.SeatStatus;
import com.eventhive.eventhive_backend.exception.AppException;
import com.eventhive.eventhive_backend.exception.InvalidEventStateException;
import com.eventhive.eventhive_backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for BookingService.createBooking — the concurrency-critical method.
 *
 * Uses Mockito to mock all repository dependencies so tests run in
 * milliseconds with no database or Spring context needed.
 *
 * @ExtendWith(MockitoExtension.class): tells JUnit 5 to activate Mockito's
 * annotation processing — initializes @Mock and @InjectMocks automatically.
 *
 * @Mock: creates a mock (fake) implementation of the repository interface.
 * When the service calls seatRepository.findByIdWithLock(), we control
 * exactly what it returns — no real DB needed.
 *
 * @InjectMocks: creates a real BookingService instance and injects all
 * the @Mock fields into its constructor automatically.
 */
@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock private BookingRepository bookingRepository;
    @Mock private BookingItemRepository bookingItemRepository;
    @Mock private SeatRepository seatRepository;
    @Mock private EventRepository eventRepository;

    @InjectMocks
    private BookingService bookingService;

    // ── Test fixtures — reusable objects built in setUp() ────────────────

    private User attendee;
    private Event publishedEvent;
    private SeatCategory generalCategory;
    private Seat availableSeat;
    private CreateBookingRequest request;

    @BeforeEach
    void setUp() {
        // Attendee
        attendee = new User();
        attendee.setId(1L);
        attendee.setName("Test Attendee");
        attendee.setEmail("attendee@test.com");

        // Published event — only PUBLISHED events accept bookings
        publishedEvent = new Event();
        publishedEvent.setId(10L);
        publishedEvent.setTitle("Tech Conference 2026");
        publishedEvent.setStatus(EventStatus.PUBLISHED);
        publishedEvent.setAvailableSeats(100);

        // Seat category with price
        generalCategory = new SeatCategory();
        generalCategory.setId(1L);
        generalCategory.setName("General");
        generalCategory.setPrice(new BigDecimal("500.00"));

        // An available seat
        availableSeat = new Seat();
        availableSeat.setId(1L);
        availableSeat.setSeatNumber("GEN-001");
        availableSeat.setStatus(SeatStatus.AVAILABLE);
        availableSeat.setEvent(publishedEvent);
        availableSeat.setSeatCategory(generalCategory);

        // Booking request for seat 1 on event 10
        request = new CreateBookingRequest();
        request.setEventId(10L);
        request.setSeatIds(List.of(1L));
    }

    /**
     * Happy path — booking an available seat should succeed.
     *
     * Verifies:
     * - Seat status changes from AVAILABLE → RESERVED
     * - Booking is saved to the repository
     * - Response contains PENDING status and correct amount
     */
    @Test
    void createBooking_shouldSucceed_whenSeatIsAvailable() {
        // Arrange — tell mocks what to return
        when(eventRepository.findById(10L))
                .thenReturn(Optional.of(publishedEvent));
        when(seatRepository.findByIdWithLock(1L))
                .thenReturn(Optional.of(availableSeat));

        // Mock the booking save — return the booking with an id set
        when(bookingRepository.save(any(Booking.class)))
                .thenAnswer(invocation -> {
                    Booking b = invocation.getArgument(0);
                    b.setId(100L);
                    return b;
                });
        when(bookingItemRepository.saveAll(any()))
                .thenReturn(List.of());

        // Act
        BookingResponse response = bookingService.createBooking(request, attendee);

        // Assert
        assertNotNull(response);
        assertEquals("PENDING", response.getStatus());
        assertEquals(new BigDecimal("500.00"), response.getTotalAmount());

        // The seat should now be RESERVED — dirty checking in production,
        // but we verify the state was set correctly
        assertEquals(SeatStatus.RESERVED, availableSeat.getStatus());

        // Verify repository interactions
        verify(bookingRepository, times(1)).save(any(Booking.class));
        verify(bookingItemRepository, times(1)).saveAll(any());
    }

    /**
     * Double-booking prevention — the core concurrency safety test.
     *
     * If a seat is already RESERVED (held by another booking in progress),
     * the service must throw AppException with CONFLICT status.
     *
     * This is exactly what SERIALIZABLE isolation + pessimistic locking
     * protects against in production — this test verifies the application-
     * level check that runs AFTER the lock is acquired.
     *
     * Interview Q: "How do you test concurrency safety?"
     * Answer: "I test the application-level guard directly — the method
     * throws AppException when a seat is RESERVED. In production, the
     * DB-level SERIALIZABLE isolation and pessimistic lock ensure only
     * one thread reaches this check for any given seat at a time."
     */
    @Test
    void createBooking_shouldThrowException_whenSeatIsAlreadyReserved() {
        // Arrange — seat is RESERVED (already held by another booking)
        availableSeat.setStatus(SeatStatus.RESERVED);

        when(eventRepository.findById(10L))
                .thenReturn(Optional.of(publishedEvent));
        when(seatRepository.findByIdWithLock(1L))
                .thenReturn(Optional.of(availableSeat));

        // Act & Assert
        AppException exception = assertThrows(AppException.class,
                () -> bookingService.createBooking(request, attendee));

        assertTrue(exception.getMessage().contains("not available"));

        // Booking should NEVER be saved when validation fails
        verify(bookingRepository, never()).save(any(Booking.class));
    }

    /**
     * Event status guard — bookings only allowed on PUBLISHED events.
     *
     * A DRAFT or PENDING_APPROVAL event should reject booking attempts
     * immediately, before any seat locking happens.
     */
    @Test
    void createBooking_shouldThrowException_whenEventIsNotPublished() {
        // Arrange — event is in DRAFT state
        publishedEvent.setStatus(EventStatus.DRAFT);

        when(eventRepository.findById(10L))
                .thenReturn(Optional.of(publishedEvent));

        // Act & Assert
        assertThrows(InvalidEventStateException.class,
                () -> bookingService.createBooking(request, attendee));

        // Seat repository should never be touched for a non-PUBLISHED event
        verify(seatRepository, never()).findByIdWithLock(any());
        verify(bookingRepository, never()).save(any(Booking.class));
    }

    /**
     * Seat ownership check — a seat from a different event cannot be booked.
     *
     * This prevents a crafted request from booking seats across events
     * by submitting a seat ID that belongs to a different event.
     */
    @Test
    void createBooking_shouldThrowException_whenSeatBelongsToDifferentEvent() {
        // Arrange — seat belongs to event 99, not event 10
        Event differentEvent = new Event();
        differentEvent.setId(99L);
        availableSeat.setEvent(differentEvent);

        when(eventRepository.findById(10L))
                .thenReturn(Optional.of(publishedEvent));
        when(seatRepository.findByIdWithLock(1L))
                .thenReturn(Optional.of(availableSeat));

        // Act & Assert
        AppException exception = assertThrows(AppException.class,
                () -> bookingService.createBooking(request, attendee));

        assertTrue(exception.getMessage().contains("does not belong to this event"));
        verify(bookingRepository, never()).save(any(Booking.class));
    }
}