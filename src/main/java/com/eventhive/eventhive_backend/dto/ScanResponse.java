package com.eventhive.eventhive_backend.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ScanResponse {
    private boolean valid;
    private String message;
    private String attendeeName;
    private String eventTitle;
    private String seatNumbers;
}