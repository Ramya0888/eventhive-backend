package com.eventhive.eventhive_backend.controller;

import com.eventhive.eventhive_backend.dto.ApiResponse;
import com.eventhive.eventhive_backend.dto.CreateEventRequest;
import com.eventhive.eventhive_backend.dto.EventResponse;
import com.eventhive.eventhive_backend.dto.UpdateEventRequest;
import com.eventhive.eventhive_backend.security.CustomUserDetails;
import com.eventhive.eventhive_backend.service.EventService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import com.eventhive.eventhive_backend.entity.User;
@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<ApiResponse<EventResponse>> createEvent(
            @Valid @RequestBody CreateEventRequest request,
            @AuthenticationPrincipal CustomUserDetails principal) {

        EventResponse response = eventService.createEvent(request, principal.getUser());

        return ResponseEntity
                .status(HttpStatus.CREATED)  // 201
                .body(ApiResponse.success("Event created successfully", response));
    }

    /**
     * GET /api/events — public catalog of PUBLISHED events.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<EventResponse>>> getPublishedEvents() {
        List<EventResponse> events = eventService.getPublishedEvents();
        return ResponseEntity.ok(ApiResponse.success("Events fetched", events));
    }

    /**
     * GET /api/events/my-events — the logged-in organizer's own events (incl. DRAFTs).
     * Declared BEFORE /{id} so the literal path isn't captured as an id.
     */
    @GetMapping("/my-events")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<ApiResponse<List<EventResponse>>> getMyEvents(
            @AuthenticationPrincipal CustomUserDetails principal) {
        List<EventResponse> events = eventService.getMyEvents(principal.getUser());
        return ResponseEntity.ok(ApiResponse.success("Your events fetched", events));
    }

    /**
     * GET /api/events/{id} — single event detail (public).
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<EventResponse>> getEventById(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails principal) {

        // principal is null when the caller is anonymous (public access).
        User requester = (principal != null) ? principal.getUser() : null;

        EventResponse event = eventService.getEventById(id, requester);
        return ResponseEntity.ok(ApiResponse.success("Event fetched", event));
    }

    /**
     * PATCH /api/events/{id}/submit — organizer submits DRAFT for approval.
     */
    @PatchMapping("/{id}/submit")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<ApiResponse<EventResponse>> submit(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails principal) {
        EventResponse e = eventService.submitForApproval(id, principal.getUser());
        return ResponseEntity.ok(ApiResponse.success("Event submitted for approval", e));
    }

    /**
     * PATCH /api/events/{id}/approve — admin approves -> PUBLISHED.
     */
    @PatchMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<EventResponse>> approve(@PathVariable Long id) {
        EventResponse e = eventService.approveEvent(id);
        return ResponseEntity.ok(ApiResponse.success("Event approved and published", e));
    }

    /**
     * PATCH /api/events/{id}/reject — admin rejects -> back to DRAFT.
     */
    @PatchMapping("/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<EventResponse>> reject(@PathVariable Long id) {
        EventResponse e = eventService.rejectEvent(id);
        return ResponseEntity.ok(ApiResponse.success("Event rejected", e));
    }

    /**
     * PATCH /api/events/{id}/cancel — organizer cancels their event.
     */
    @PatchMapping("/{id}/cancel")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<ApiResponse<EventResponse>> cancel(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails principal) {
        EventResponse e = eventService.cancelEvent(id, principal.getUser());
        return ResponseEntity.ok(ApiResponse.success("Event cancelled", e));
    }

    /**
     * PUT /api/events/{id} — organizer edits their DRAFT event (full replacement).
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<ApiResponse<EventResponse>> updateEvent(
            @PathVariable Long id,
            @Valid @RequestBody UpdateEventRequest request,
            @AuthenticationPrincipal CustomUserDetails principal) {

        EventResponse e = eventService.updateEvent(id, request, principal.getUser());
        return ResponseEntity.ok(ApiResponse.success("Event updated successfully", e));
    }
}