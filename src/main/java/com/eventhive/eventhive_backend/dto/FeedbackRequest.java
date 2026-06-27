package com.eventhive.eventhive_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FeedbackRequest {

    @NotNull(message = "Event id is required")
    private Long eventId;

    @NotBlank(message = "Feedback content is required")
    @Size(min = 10, max = 2000, message = "Feedback must be between 10 and 2000 characters")
    private String content;
}