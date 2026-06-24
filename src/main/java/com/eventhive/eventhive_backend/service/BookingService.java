package com.eventhive.eventhive_backend.service;

import com.eventhive.eventhive_backend.dto.BookingResponse;
import com.eventhive.eventhive_backend.dto.CreateBookingRequest;
import com.eventhive.eventhive_backend.entity.*;
import com.eventhive.eventhive_backend.enums.BookingStatus;
import com.eventhive.eventhive_backend.enums.SeatStatus;
import com.eventhive.eventhive_backend.exception.AppException;
import com.eventhive.eventhive_backend.exception.InvalidEventStateException;
import com.eventhive.eventhive_backend.exception.ResourceNotFoundException;
import com.eventhive.eventhive_backend.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final BookingItemRepository bookingItemRepository;
    private final SeatRepository seatRepository;
    private final EventRepository eventRepository;

    public BookingService(BookingRepository bookingRepository,
                          BookingItemRepository bookingItemRepository,
                          SeatRepository seatRepository,
                          EventRepository eventRepository) {
        this.bookingRepository = bookingRepository;
        this.bookingItemRepository = bookingItemRepository;
        this.seatRepository = seatRepository;
        this.eventRepository = eventRepository;
    }

    /**
     * CREATE BOOKING — the concurrency-critical method.
     *
     * Isolation.SERIALIZABLE: the strictest isolation level. Prevents
     * phantom reads — no other transaction can insert or modify rows
     * that would affect this transaction's reads. Combined with
     * pessimistic locking on seats, this is the double defence against
     * double-booking.
     *
     * Interview Q: "Why SERIALIZABLE and not REPEATABLE_READ (the default)?"
     * REPEATABLE_READ prevents dirty reads and non-repeatable reads, but
     * allows phantom reads — new rows can appear mid-transaction. For seat
     * booking, a phantom seat appearing between our availability check and
     * our lock acquisition could cause a double-booking. SERIALIZABLE
     * eliminates that window entirely.
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public BookingResponse createBooking(CreateBookingRequest request, User attendee) {
        log.info("User {} attempting to book {} seats for event {}",
                attendee.getId(), request.getSeatIds().size(), request.getEventId());

        // 1. Verify event exists and is PUBLISHED (can't book a DRAFT).
        Event event = eventRepository.findById(request.getEventId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Event not found: " + request.getEventId()));

        if (event.getStatus().name().equals("PUBLISHED") == false) {
            throw new InvalidEventStateException(
                    "Bookings are only open for PUBLISHED events.");
        }

        // 2. Lock and validate each requested seat.
        // SORT seat ids before locking — consistent lock ordering prevents deadlocks.
        // If thread A locks seat 1 then seat 2, and thread B locks seat 2 then seat 1,
        // they deadlock. Sorting means everyone locks in the same order.
        List<Long> sortedSeatIds = request.getSeatIds().stream()
                .sorted()
                .collect(Collectors.toList());

        List<Seat> seats = sortedSeatIds.stream()
                .map(seatId -> seatRepository.findByIdWithLock(seatId)
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Seat not found: " + seatId)))
                .collect(Collectors.toList());

        // 3. Validate all seats belong to this event and are AVAILABLE.
        for (Seat seat : seats) {
            if (!seat.getEvent().getId().equals(request.getEventId())) {
                throw new AppException("Seat " + seat.getSeatNumber()
                        + " does not belong to this event", HttpStatus.BAD_REQUEST);
            }
            if (seat.getStatus() != SeatStatus.AVAILABLE) {
                throw new AppException("Seat " + seat.getSeatNumber()
                        + " is not available (status: " + seat.getStatus() + ")",
                        HttpStatus.CONFLICT);
            }
        }

        // 4. Reserve all seats — status AVAILABLE → RESERVED.
        // Dirty checking: no explicit save() needed; Hibernate flushes on commit.
        seats.forEach(seat -> seat.setStatus(SeatStatus.RESERVED));

        // 5. Calculate total amount from seat category prices.
        BigDecimal totalAmount = seats.stream()
                .map(seat -> seat.getSeatCategory().getPrice())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 6. Create the booking.
        LocalDateTime now = LocalDateTime.now();
        String reference = generateReference();

        Booking booking = Booking.builder()
                .bookingReference(reference)
                .user(attendee)
                .event(event)
                .totalAmount(totalAmount)
                .status(BookingStatus.PENDING)
                .reservedAt(now)
                .expiresAt(now.plusMinutes(10))  // 10-minute payment window
                .build();
        booking = bookingRepository.save(booking);

        // 7. Create booking items — one per seat, with price snapshot.
        final Booking savedBooking = booking;
        List<BookingItem> items = seats.stream()
                .map(seat -> BookingItem.builder()
                        .booking(savedBooking)
                        .seat(seat)
                        .seatCategory(seat.getSeatCategory())
                        .priceAtBooking(seat.getSeatCategory().getPrice())
                        .build())
                .collect(Collectors.toList());
        bookingItemRepository.saveAll(items);
        booking.setItems(items);

        // 8. Update available seat count on the event.
        event.setAvailableSeats(event.getAvailableSeats() - seats.size());

        log.info("Booking {} created for user {} — {} seats reserved until {}",
                reference, attendee.getId(), seats.size(), booking.getExpiresAt());

        return BookingResponse.from(booking);
    }

    /**
     * CANCEL BOOKING — attendee cancels their own booking.
     * Releases reserved/booked seats back to AVAILABLE.
     */
    @Transactional
    public BookingResponse cancelBooking(Long bookingId, User attendee) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Booking not found: " + bookingId));

        // Ownership check — 404 for non-disclosure.
        if (!booking.getUser().getId().equals(attendee.getId())) {
            throw new ResourceNotFoundException("Booking not found: " + bookingId);
        }

        if (booking.getStatus() == BookingStatus.CANCELLED ||
            booking.getStatus() == BookingStatus.REFUNDED) {
            throw new AppException("Booking is already " + booking.getStatus(),
                    HttpStatus.CONFLICT);
        }

        // Release all seats back to AVAILABLE.
        int seatCount = releaseSeats(booking);

        booking.setStatus(BookingStatus.CANCELLED);

        // Restore available seat count on the event.
        Event event = booking.getEvent();
        event.setAvailableSeats(event.getAvailableSeats() + seatCount);

        log.info("Booking {} cancelled — {} seats released", booking.getBookingReference(), seatCount);
        return BookingResponse.from(booking);
    }

    /**
     * GET attendee's own bookings.
     */
    @Transactional(readOnly = true)
    public List<BookingResponse> getMyBookings(User attendee) {
        return bookingRepository.findByUserIdOrderByCreatedAtDesc(attendee.getId())
                .stream()
                .map(BookingResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * SCHEDULED CLEANUP — runs every 60 seconds.
     * Finds PENDING bookings whose 10-minute window has expired and
     * releases their seats back to AVAILABLE.
     *
     * @Scheduled: Spring runs this in a background thread automatically.
     * fixedDelay = 60_000ms = 1 minute between the END of one run
     * and the START of the next (safer than fixedRate if the job takes
     * longer than expected).
     *
     * Interview Q: "What happens if someone doesn't complete payment?"
     * Answer: A scheduled job runs every minute, finds PENDING bookings
     * past their expiresAt timestamp, releases the seats, and marks the
     * booking CANCELLED — so no seat is held indefinitely.
     */
    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void releaseExpiredReservations() {
        List<Booking> expired = bookingRepository.findExpiredBookings(
                BookingStatus.PENDING, LocalDateTime.now());

        if (expired.isEmpty()) return;

        log.info("Cleanup job: releasing {} expired reservations", expired.size());

        for (Booking booking : expired) {
            int seatCount = releaseSeats(booking);
            booking.setStatus(BookingStatus.CANCELLED);
            booking.getEvent().setAvailableSeats(
                    booking.getEvent().getAvailableSeats() + seatCount);
            log.info("Released expired booking {} — {} seats freed",
                    booking.getBookingReference(), seatCount);
        }
    }

    // ── Private helpers ──────────────────────────────────────────

    private int releaseSeats(Booking booking) {
        List<BookingItem> items = bookingItemRepository.findByBookingId(booking.getId());
        items.forEach(item -> item.getSeat().setStatus(SeatStatus.AVAILABLE));
        return items.size();
    }

    private String generateReference() {
        // EVH + first 8 chars of a UUID (uppercase, no hyphens).
        // Collision probability is negligible for this scale.
        return "EVH" + UUID.randomUUID().toString()
                .replace("-", "").substring(0, 8).toUpperCase();
    }
}