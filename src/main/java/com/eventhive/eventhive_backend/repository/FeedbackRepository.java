package com.eventhive.eventhive_backend.repository;

import com.eventhive.eventhive_backend.entity.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Map;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

    List<Feedback> findByEventId(Long eventId);

    // Check if this user already submitted feedback for this event
    boolean existsByUserIdAndEventId(Long userId, Long eventId);

    /**
     * Sentiment breakdown for an event — used by the organizer dashboard.
     * Returns count of each sentiment: POSITIVE, NEUTRAL, NEGATIVE.
     * GROUP BY sentiment gives one row per sentiment type.
     */
    @Query("""
            SELECT f.sentiment AS sentiment, COUNT(f) AS count
            FROM Feedback f
            WHERE f.event.id = :eventId
              AND f.sentiment IS NOT NULL
            GROUP BY f.sentiment
            """)
    List<Map<String, Object>> getSentimentBreakdown(@Param("eventId") Long eventId);
}