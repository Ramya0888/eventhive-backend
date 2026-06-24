package com.eventhive.eventhive_backend.enums;

/**
 * BookingStatus — lifecycle of a booking.
 *
 * PENDING    → seats reserved, awaiting payment (10-minute window)
 * CONFIRMED  → payment successful, seats permanently booked
 * CANCELLED  → attendee cancelled or payment failed
 * REFUNDED   → confirmed booking later refunded 
 */
public enum BookingStatus {
    PENDING,
    CONFIRMED,
    CANCELLED,
    REFUNDED
}