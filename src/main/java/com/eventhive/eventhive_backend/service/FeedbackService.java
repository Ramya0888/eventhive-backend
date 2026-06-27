package com.eventhive.eventhive_backend.service;

import com.eventhive.eventhive_backend.dto.FeedbackRequest;
import com.eventhive.eventhive_backend.dto.FeedbackResponse;
import com.eventhive.eventhive_backend.entity.Event;
import com.eventhive.eventhive_backend.entity.Feedback;
import com.eventhive.eventhive_backend.entity.User;
import com.eventhive.eventhive_backend.enums.EventStatus;
import com.eventhive.eventhive_backend.exception.AppException;
import com.eventhive.eventhive_backend.exception.ResourceNotFoundException;
import com.eventhive.eventhive_backend.repository.BookingRepository;
import com.eventhive.eventhive_backend.repository.EventRepository;
import com.eventhive.eventhive_backend.repository.FeedbackRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class FeedbackService {

    private final FeedbackRepository feedbackRepository;
    private final EventRepository eventRepository;
    private final BookingRepository bookingRepository;
    private final AIService aiService;

    public FeedbackService(FeedbackRepository feedbackRepository,
                           EventRepository eventRepository,
                           BookingRepository bookingRepository,
                           AIService aiService) {
        this.feedbackRepository = feedbackRepository;
        this.eventRepository = eventRepository;
        this.bookingRepository = bookingRepository;
        this.aiService = aiService;
    }

    /**
     * SUBMIT FEEDBACK — attendee posts feedback on an event they attended.
     *
     * Validations:
     * 1. Event must exist and be COMPLETED or ONGOING (can't review a future event)
     * 2. Attendee must have a CONFIRMED booking for this event
     * 3. One feedback per user per event
     *
     * Sentiment analysis runs @Async — feedback is saved immediately,
     * sentiment populated in the background. API responds without waiting
     * for Gemini (which could take 2–3 seconds).
     */
    @Transactional
    public FeedbackResponse submitFeedback(FeedbackRequest request, User attendee) {
        Event event = eventRepository.findById(request.getEventId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Event not found: " + request.getEventId()));

      // Allow feedback if the event date has passed or event is actively happening.
// We check the actual date rather than relying solely on status because there's
// always a scheduler lag between when an event starts and when status updates.
boolean eventHasStarted = event.getEventDate().isBefore(LocalDate.now())
        || event.getEventDate().isEqual(LocalDate.now())
        || event.getStatus() == EventStatus.ONGOING
        || event.getStatus() == EventStatus.COMPLETED;

if (!eventHasStarted) {
    throw new AppException(
            "Feedback can only be submitted after the event has started.",
            HttpStatus.CONFLICT);
}
        // Must have a confirmed booking — can't review an event you didn't attend
        boolean hasConfirmedBooking = bookingRepository
                .findByUserIdOrderByCreatedAtDesc(attendee.getId())
                .stream()
                .anyMatch(b -> b.getEvent().getId().equals(event.getId())
                        && b.getStatus().name().equals("CONFIRMED"));

        if (!hasConfirmedBooking) {
            throw new AppException(
                    "You must have a confirmed booking to submit feedback.",
                    HttpStatus.FORBIDDEN);
        }

        // One feedback per user per event
        if (feedbackRepository.existsByUserIdAndEventId(
                attendee.getId(), event.getId())) {
            throw new AppException(
                    "You have already submitted feedback for this event.",
                    HttpStatus.CONFLICT);
        }

        // Save feedback immediately — sentiment filled async
        Feedback feedback = Feedback.builder()
                .user(attendee)
                .event(event)
                .content(request.getContent())
                .sentiment(null)         // will be set by async job
                .confidenceScore(null)
                .build();
        feedback = feedbackRepository.save(feedback);

        // Trigger async sentiment analysis — returns immediately
        analyzeSentimentAsync(feedback.getId(), request.getContent());

        log.info("Feedback {} saved for event {} — sentiment analysis queued",
                feedback.getId(), event.getId());

        return FeedbackResponse.from(feedback);
    }

    /**
     * Runs in a background thread after feedback is saved.
     * Calls Gemini, parses the SENTIMENT:CONFIDENCE response,
     * updates the feedback row.
     *
     * @Async: the HTTP response for submitFeedback returns immediately
     * while this runs concurrently. If Gemini fails, the feedback
     * stays saved (just without sentiment) — non-critical error.
     */
    @Async
    @Transactional
    public void analyzeSentimentAsync(Long feedbackId, String content) {
        try {
            String result = aiService.analyzeSentiment(content);
            // Expected format: "POSITIVE:0.92"
            String[] parts = result.split(":");
            if (parts.length == 2) {
                String sentiment = parts[0].trim().toUpperCase();
                double confidence = Double.parseDouble(parts[1].trim());

                Feedback feedback = feedbackRepository.findById(feedbackId)
                        .orElse(null);
                if (feedback != null) {
                    feedback.setSentiment(sentiment);
                    feedback.setConfidenceScore(confidence);
                    feedbackRepository.save(feedback);
                    log.info("Sentiment for feedback {}: {} ({})",
                            feedbackId, sentiment, confidence);
                }
            }
        } catch (Exception e) {
            log.error("Sentiment analysis failed for feedback {}: {}",
                    feedbackId, e.getMessage());
        }
    }

    /**
     * GET feedback for an event — for the organizer to see what attendees said.
     */
    @Transactional(readOnly = true)
    public List<FeedbackResponse> getEventFeedback(Long eventId, User organizer) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Event not found: " + eventId));
    
        // Ownership check — 404 (not 403) for non-disclosure
        if (!event.getOrganizer().getId().equals(organizer.getId())) {
            throw new ResourceNotFoundException("Event not found: " + eventId);
        }
    
        return feedbackRepository.findByEventId(eventId)
                .stream()
                .map(FeedbackResponse::from)
                .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getSentimentBreakdown(Long eventId, User organizer) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Event not found: " + eventId));
    
        if (!event.getOrganizer().getId().equals(organizer.getId())) {
            throw new ResourceNotFoundException("Event not found: " + eventId);
        }
    
        return feedbackRepository.getSentimentBreakdown(eventId);
    }
}