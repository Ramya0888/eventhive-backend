package com.eventhive.eventhive_backend.controller;

import com.eventhive.eventhive_backend.dto.ApiResponse;
import com.eventhive.eventhive_backend.service.AIService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AIController {

    private final AIService aiService;

    public AIController(AIService aiService) {
        this.aiService = aiService;
    }

    /**
     * POST /api/ai/generate-description
     * Organizer sends event title + category, gets a generated description back.
     * ORGANIZER only — attendees don't create events.
     *
     * Error handling:
     * 429 from Gemini → 503 Service Unavailable (friendly message, retry later)
     * Other errors   → 500 Internal Server Error (generic message)
     * Both return ApiResponse so the frontend handles them consistently —
     * it reads response.data.message and shows it to the user.
     */
    @PostMapping("/generate-description")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<ApiResponse<String>> generateDescription(
            @RequestBody Map<String, String> request) {

        String title    = request.get("title");
        String category = request.get("category");

        if (title == null || title.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Title is required"));
        }
        if (category == null || category.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Category is required"));
        }

        try {
            String description = aiService.generateEventDescription(title, category);
            return ResponseEntity.ok(
                    ApiResponse.success("Description generated", description));

        } catch (RuntimeException e) {
            // Gemini rate limit — 429 comes through as a RuntimeException
            // with the status code in the message string
            if (e.getMessage() != null && e.getMessage().contains("429")) {
                return ResponseEntity
                        .status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(ApiResponse.error(
                            "AI service is temporarily busy. " +
                            "Please wait a moment and try again."));
            }
            // Any other error (network, auth, etc.)
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(
                        "AI description generation failed. " +
                        "You can type a description manually."));
        }
    }
}