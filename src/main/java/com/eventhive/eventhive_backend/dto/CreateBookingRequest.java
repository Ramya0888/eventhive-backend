package com.eventhive.eventhive_backend.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Attendee sends: which event and which specific seat ids they want.
 * The service validates ownership, availability, and locks the seats.
 */
@Getter
@Setter
public class CreateBookingRequest {

    @NotNull(message = "Event id is required")
    private Long eventId;

    // The specific seat ids the attendee selected from the seat map.
    @NotEmpty(message = "At least one seat must be selected")
    private List<Long> seatIds;
}