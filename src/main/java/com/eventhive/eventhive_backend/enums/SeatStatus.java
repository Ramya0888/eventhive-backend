package com.eventhive.eventhive_backend.enums;

/**
 * SeatStatus — the lifecycle of an individual seat.
 *
 * AVAILABLE  → can be selected by an attendee
 * RESERVED   → held for 10 minutes during payment (Module 4)
 * BOOKED     → payment confirmed, permanently taken
 * BLOCKED    → manually blocked by organizer (e.g. stage extension)
 */
public enum SeatStatus {
    AVAILABLE,
    RESERVED,
    BOOKED,
    BLOCKED
}