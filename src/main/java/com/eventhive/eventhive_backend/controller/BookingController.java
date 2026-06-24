package com.eventhive.eventhive_backend.controller;

import com.eventhive.eventhive_backend.dto.ApiResponse;
import com.eventhive.eventhive_backend.dto.BookingResponse;
import com.eventhive.eventhive_backend.dto.CreateBookingRequest;
import com.eventhive.eventhive_backend.entity.User;
import com.eventhive.eventhive_backend.security.CustomUserDetails;
import com.eventhive.eventhive_backend.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    /**
     * POST /api/bookings — attendee reserves seats.
     * Only ATTENDEEs book; organizers and admins manage events.
     */
    @PostMapping
    @PreAuthorize("hasRole('ATTENDEE')")
    public ResponseEntity<ApiResponse<BookingResponse>> createBooking(
            @Valid @RequestBody CreateBookingRequest request,
            @AuthenticationPrincipal CustomUserDetails principal) {

        BookingResponse response = bookingService.createBooking(
                request, principal.getUser());
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Seats reserved successfully", response));
    }

    /**
     * GET /api/bookings/my-bookings — attendee's booking history.
     */
    @GetMapping("/my-bookings")
    @PreAuthorize("hasRole('ATTENDEE')")
    public ResponseEntity<ApiResponse<List<BookingResponse>>> getMyBookings(
            @AuthenticationPrincipal CustomUserDetails principal) {

        List<BookingResponse> bookings = bookingService.getMyBookings(
                principal.getUser());
        return ResponseEntity.ok(ApiResponse.success("Bookings fetched", bookings));
    }

    /**
     * POST /api/bookings/{id}/cancel — attendee cancels their booking.
     */
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('ATTENDEE')")
    public ResponseEntity<ApiResponse<BookingResponse>> cancelBooking(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails principal) {

        BookingResponse response = bookingService.cancelBooking(
                id, principal.getUser());
        return ResponseEntity.ok(ApiResponse.success("Booking cancelled", response));
    }
}