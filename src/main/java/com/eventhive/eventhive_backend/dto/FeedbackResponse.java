package com.eventhive.eventhive_backend.dto;

import com.eventhive.eventhive_backend.entity.Feedback;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class FeedbackResponse {

    private Long id;
    private Long eventId;
    private String eventTitle;
    private String content;
    private String sentiment;
    private Double confidenceScore;
    private String createdAt;

    public static FeedbackResponse from(Feedback f) {
        return FeedbackResponse.builder()
                .id(f.getId())
                .eventId(f.getEvent().getId())
                .eventTitle(f.getEvent().getTitle())
                .content(f.getContent())
                .sentiment(f.getSentiment())
                .confidenceScore(f.getConfidenceScore())
                .createdAt(f.getCreatedAt() != null
                        ? f.getCreatedAt().toString() : null)
                .build();
    }
}