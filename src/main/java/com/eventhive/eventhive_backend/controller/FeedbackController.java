package com.eventhive.eventhive_backend.controller;

import com.eventhive.eventhive_backend.dto.ApiResponse;
import com.eventhive.eventhive_backend.dto.FeedbackRequest;
import com.eventhive.eventhive_backend.dto.FeedbackResponse;
import com.eventhive.eventhive_backend.security.CustomUserDetails;
import com.eventhive.eventhive_backend.service.FeedbackService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {

    private final FeedbackService feedbackService;

    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    /**
     * POST /api/feedback — attendee submits feedback.
     * Returns immediately; sentiment analysis happens in background.
     */
    @PostMapping
    @PreAuthorize("hasRole('ATTENDEE')")
    public ResponseEntity<ApiResponse<FeedbackResponse>> submitFeedback(
            @Valid @RequestBody FeedbackRequest request,
            @AuthenticationPrincipal CustomUserDetails principal) {

        FeedbackResponse response = feedbackService.submitFeedback(
                request, principal.getUser());
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Feedback submitted. Sentiment analysis in progress.", response));
    }

    /**
     * GET /api/feedback/event/{eventId} — organizer sees all feedback for their event.
     */
    @GetMapping("/event/{eventId}")
@PreAuthorize("hasRole('ORGANIZER')")
public ResponseEntity<ApiResponse<List<FeedbackResponse>>> getEventFeedback(
        @PathVariable Long eventId,
        @AuthenticationPrincipal CustomUserDetails principal) {

    return ResponseEntity.ok(ApiResponse.success(
            "Feedback fetched",
            feedbackService.getEventFeedback(eventId, principal.getUser())));
}

@GetMapping("/event/{eventId}/sentiment")
@PreAuthorize("hasRole('ORGANIZER')")
public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getSentimentBreakdown(
        @PathVariable Long eventId,
        @AuthenticationPrincipal CustomUserDetails principal) {

    return ResponseEntity.ok(ApiResponse.success(
            "Sentiment breakdown fetched",
            feedbackService.getSentimentBreakdown(eventId, principal.getUser())));
}
}