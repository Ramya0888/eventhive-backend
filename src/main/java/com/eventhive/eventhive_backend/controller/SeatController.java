package com.eventhive.eventhive_backend.controller;

import com.eventhive.eventhive_backend.dto.ApiResponse;
import com.eventhive.eventhive_backend.dto.SeatMapResponse;
import com.eventhive.eventhive_backend.service.SeatService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/events/{eventId}/seats")
public class SeatController {

    private final SeatService seatService;

    public SeatController(SeatService seatService) {
        this.seatService = seatService;
    }

    /**
     * GET /api/events/{eventId}/seats — returns the full seat map.
     * Public: attendees need to see which seats are available before booking.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<SeatMapResponse>> getSeatMap(
            @PathVariable Long eventId) {
        return ResponseEntity.ok(
            ApiResponse.success("Seat map fetched", seatService.getSeatMap(eventId)));
    }
}