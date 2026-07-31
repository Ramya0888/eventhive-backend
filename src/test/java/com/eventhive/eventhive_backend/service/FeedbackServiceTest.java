package com.eventhive.eventhive_backend.service;

import com.eventhive.eventhive_backend.repository.EventRepository;
import com.eventhive.eventhive_backend.repository.BookingRepository;
import com.eventhive.eventhive_backend.entity.Feedback;
import com.eventhive.eventhive_backend.repository.FeedbackRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for FeedbackService.analyzeSentimentAsync —
 * specifically the sentiment parsing logic.
 *
 * What we're testing: does the service correctly parse Gemini's
 * "POSITIVE:0.92" response format into separate sentiment and
 * confidence fields before saving to the database?
 *
 * Why this matters: if the parsing breaks (wrong delimiter, extra
 * whitespace, unexpected format), feedback gets saved with null
 * sentiment even though Gemini returned a valid response. This
 * test catches that regression.
 *
 * Note: @Async is ignored in unit tests — the method runs
 * synchronously here, which is what we want for testing.
 */
@ExtendWith(MockitoExtension.class)
class FeedbackServiceTest {

    @Mock private FeedbackRepository feedbackRepository;
    @Mock private EventRepository eventRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private AIService aiService;

    @InjectMocks
    private FeedbackService feedbackService;

    /**
     * Happy path — Gemini returns "POSITIVE:0.92".
     * Verifies the feedback is saved with sentiment="POSITIVE"
     * and confidenceScore=0.92.
     *
     * Interview Q: "How do you test async methods?"
     * Answer: "In unit tests @Async is not active — the method runs
     * synchronously on the test thread. I test the parsing and saving
     * logic directly. Integration tests would verify the async behavior,
     * but the business logic (parsing) is fully testable in isolation."
     */
    @Test
    void analyzeSentimentAsync_shouldParseSentimentAndConfidence_fromPositiveResponse() {
        // Arrange
        Feedback feedback = new Feedback();
        feedback.setId(1L);
        feedback.setContent("Amazing event, learned a lot!");

        when(aiService.analyzeSentiment("Amazing event, learned a lot!"))
                .thenReturn("POSITIVE:0.92");
        when(feedbackRepository.findById(1L))
                .thenReturn(Optional.of(feedback));
        when(feedbackRepository.save(any(Feedback.class)))
                .thenReturn(feedback);

        // Act
        feedbackService.analyzeSentimentAsync(1L, "Amazing event, learned a lot!");

        // Assert — verify the feedback was saved with correct parsed values
        verify(feedbackRepository).save(argThat(saved ->
                "POSITIVE".equals(saved.getSentiment())
                && saved.getConfidenceScore() == 0.92
        ));
    }

    /**
     * Negative sentiment — Gemini returns "NEGATIVE:0.88".
     * Same parsing logic, different values.
     */
    @Test
    void analyzeSentimentAsync_shouldParseSentimentAndConfidence_fromNegativeResponse() {
        // Arrange
        Feedback feedback = new Feedback();
        feedback.setId(2L);
        feedback.setContent("Very disappointing, poor organization.");

        when(aiService.analyzeSentiment("Very disappointing, poor organization."))
                .thenReturn("NEGATIVE:0.88");
        when(feedbackRepository.findById(2L))
                .thenReturn(Optional.of(feedback));
        when(feedbackRepository.save(any(Feedback.class)))
                .thenReturn(feedback);

        // Act
        feedbackService.analyzeSentimentAsync(2L,
                "Very disappointing, poor organization.");

        // Assert
        verify(feedbackRepository).save(argThat(saved ->
                "NEGATIVE".equals(saved.getSentiment())
                && saved.getConfidenceScore() == 0.88
        ));
    }

    /**
     * Malformed response — Gemini returns something unexpected
     * (no colon delimiter, wrong format).
     *
     * The service should silently swallow this without crashing,
     * and NOT save the feedback with garbage data.
     *
     * This tests the defensive parsing — if parts.length != 2,
     * the service skips the save entirely.
     *
     * Interview Q: "What happens if the AI returns an unexpected format?"
     * Answer: "The parsing checks that the response splits into exactly
     * 2 parts on ':'. If it doesn't, we skip the update silently — the
     * feedback stays saved but with null sentiment. A log error is
     * recorded for observability. The booking is already confirmed so
     * a missing sentiment is non-critical."
     */
    @Test
    void analyzeSentimentAsync_shouldNotSave_whenResponseIsMalformed() {
        // Arrange — Gemini returns something unparseable
        when(aiService.analyzeSentiment(anyString()))
                .thenReturn("INVALID_RESPONSE_NO_COLON");

        // Act — should not throw, should handle gracefully
        assertDoesNotThrow(() ->
                feedbackService.analyzeSentimentAsync(1L, "some feedback"));

        // Assert — save should never be called with garbage data
        verify(feedbackRepository, never()).save(any(Feedback.class));
    }

    /**
     * Gemini API failure — the AI service throws an exception.
     *
     * The @Async method catches all exceptions silently so the
     * background thread doesn't crash. The feedback stays saved
     * with null sentiment — non-critical failure.
     */
    @Test
    void analyzeSentimentAsync_shouldNotThrow_whenAiServiceFails() {
        // Arrange — AI service is down or rate-limited
        when(aiService.analyzeSentiment(anyString()))
                .thenThrow(new RuntimeException("Gemini API unavailable"));

        // Act & Assert — exception is swallowed, no crash
        assertDoesNotThrow(() ->
                feedbackService.analyzeSentimentAsync(1L, "some feedback"));

        // Save should never be called when AI fails
        verify(feedbackRepository, never()).save(any(Feedback.class));
    }
}