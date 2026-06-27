package com.eventhive.eventhive_backend.service;

import com.eventhive.eventhive_backend.entity.Booking;
import com.eventhive.eventhive_backend.entity.BookingItem;
import com.eventhive.eventhive_backend.repository.BookingItemRepository;
import com.eventhive.eventhive_backend.repository.BookingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;


@Service
public class TicketDataLoader {

    private final BookingRepository bookingRepository;
    private final BookingItemRepository bookingItemRepository;

    public TicketDataLoader(BookingRepository bookingRepository,
                            BookingItemRepository bookingItemRepository) {
        this.bookingRepository = bookingRepository;
        this.bookingItemRepository = bookingItemRepository;
    }

    /**
     * Loads all booking data needed for the ticket PDF and email.
     * Runs in a real @Transactional — all LAZY associations are
     * initialized while the session is open, then extracted into
     * plain strings. No JPA proxies leave this method.
     */
    @Transactional(readOnly = true)
    public TicketService.TicketData load(Long bookingId) {
        Booking b = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException(
                        "Booking not found: " + bookingId));

        // Touch all LAZY associations while session is open
        List<BookingItem> items =
                bookingItemRepository.findByBookingId(bookingId);

        // Extract seat info into plain strings — no proxies cross the boundary
        List<String> seatLines = items.stream()
                .map(item -> item.getSeat().getSeatNumber()
                        + "  (" + item.getSeatCategory().getName()
                        + ")  — INR " + item.getPriceAtBooking())
                .collect(Collectors.toList());

        // Extract all needed values as plain Java types
        return new TicketService.TicketData(
                b.getBookingReference(),
                b.getEvent().getTitle(),
                b.getEvent().getEventDate().toString(),
                b.getEvent().getVenue().getName(),
                b.getEvent().getVenue().getCity(),
                b.getUser().getName(),
                b.getUser().getEmail(),
                b.getTotalAmount(),
                seatLines
        );
    }
}