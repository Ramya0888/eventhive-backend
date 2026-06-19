package com.eventhive.eventhive_backend.dto;

import com.eventhive.eventhive_backend.entity.Event;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Builder
public class EventResponse {

    private Long id;
    private String title;
    private String description;
    private String status;
    private LocalDate eventDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private Integer availableSeats;
    private String categoryName;
    private String organizerName;
    private String venueName;
    private String venueCity;

    // Static factory: maps a managed Event entity -> flat DTO.
   
    public static EventResponse from(Event event) {
        return EventResponse.builder()
                .id(event.getId())
                .title(event.getTitle())
                .description(event.getDescription())
                .status(event.getStatus().name())
                .eventDate(event.getEventDate())
                .startTime(event.getStartTime())
                .endTime(event.getEndTime())
                .availableSeats(event.getAvailableSeats())
                .categoryName(event.getCategory() != null ? event.getCategory().getName() : null)
                .organizerName(event.getOrganizer().getName())
                .venueName(event.getVenue() != null ? event.getVenue().getName() : null)
                .venueCity(event.getVenue() != null ? event.getVenue().getCity() : null)
                .build();
    }
}