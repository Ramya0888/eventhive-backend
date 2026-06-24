package com.eventhive.eventhive_backend.service;

import com.eventhive.eventhive_backend.dto.SeatMapResponse;
import com.eventhive.eventhive_backend.entity.Seat;
import com.eventhive.eventhive_backend.entity.SeatCategory;
import com.eventhive.eventhive_backend.exception.ResourceNotFoundException;
import com.eventhive.eventhive_backend.repository.EventRepository;
import com.eventhive.eventhive_backend.repository.SeatCategoryRepository;
import com.eventhive.eventhive_backend.repository.SeatRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class SeatService {

    private final SeatRepository seatRepository;
    private final SeatCategoryRepository seatCategoryRepository;
    private final EventRepository eventRepository;

    public SeatService(SeatRepository seatRepository,
                       SeatCategoryRepository seatCategoryRepository,
                       EventRepository eventRepository) {
        this.seatRepository = seatRepository;
        this.seatCategoryRepository = seatCategoryRepository;
        this.eventRepository = eventRepository;
    }

    /**
     * Returns the seat map for an event — all categories with their seats.
     * Grouped by category so the frontend can render tier sections.
     */
    @Transactional(readOnly = true)
    public SeatMapResponse getSeatMap(Long eventId) {
        // Verify the event exists
        eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + eventId));

        List<SeatCategory> categories = seatCategoryRepository.findByEventId(eventId);

        List<SeatMapResponse.SeatCategoryDto> categoryDtos = categories.stream()
                .map(cat -> {
                    List<Seat> seats = seatRepository.findByEventId(eventId)
                            .stream()
                            .filter(s -> s.getSeatCategory().getId().equals(cat.getId()))
                            .collect(Collectors.toList());
                    return SeatMapResponse.SeatCategoryDto.from(cat, seats);
                })
                .collect(Collectors.toList());

        return SeatMapResponse.builder()
                .eventId(eventId)
                .categories(categoryDtos)
                .build();
    }
}