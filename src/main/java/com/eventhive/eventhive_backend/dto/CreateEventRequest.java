package com.eventhive.eventhive_backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.math.BigDecimal;
    import java.util.List;

/**
 * Incoming JSON for POST /api/events.
 * The venue is nested — the organizer defines it inline with the event,
 * and both are persisted together in one transaction (Option A).
 */
@Getter
@Setter
public class CreateEventRequest {

    @NotBlank(message = "Title is required")
    @Size(max = 200, message = "Title must be at most 200 characters")
    private String title;

    @Size(max = 5000, message = "Description is too long")
    private String description;

    // The organizer picks an existing category by id (Music, Tech, ...).
    @NotNull(message = "Category is required")
    private Long categoryId;

    @NotNull(message = "Event date is required")
    @FutureOrPresent(message = "Event date cannot be in the past")
    private LocalDate eventDate;

    private LocalTime startTime;
    private LocalTime endTime;

  
    
    @NotNull(message = "Venue is required")
    @Valid
    private VenueRequest venue;

    @Getter
    @Setter
    public static class VenueRequest {
        @NotBlank(message = "Venue name is required")
        private String name;

        @NotBlank(message = "Venue address is required")
        private String address;

        @NotBlank(message = "Venue city is required")
        private String city;
    }
    // List of seat category definitions — the service generates
    // individual seat rows from these.
    @NotNull(message = "At least one seat category is required")
    @Size(min = 1, message = "At least one seat category is required")
    private List<@Valid SeatCategoryRequest> seatCategories;

    @Getter
    @Setter
    public static class SeatCategoryRequest {

        @NotBlank(message = "Category name is required")
        private String name;

        @NotNull(message = "Price is required")
        @DecimalMin(value = "0.0", message = "Price must be non-negative")
        private BigDecimal price;

        @Min(value = 1, message = "Count must be at least 1")
        private int count;
    }
}