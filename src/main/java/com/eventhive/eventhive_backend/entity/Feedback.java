package com.eventhive.eventhive_backend.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "feedback", indexes = {
        @Index(name = "idx_feedback_event_sentiment",
               columnList = "event_id, sentiment")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class Feedback extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    // Populated asynchronously by Gemini after submission.
    // Nullable until AI analysis completes.
    @Column(length = 20)
    private String sentiment;

    @Column(name = "confidence_score")
    private Double confidenceScore;
}