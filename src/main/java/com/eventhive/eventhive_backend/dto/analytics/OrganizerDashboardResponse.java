package com.eventhive.eventhive_backend.dto.analytics;

import lombok.Builder;
import lombok.Getter;
import java.math.BigDecimal;
import java.util.List;

/**
 * Response for organizer's analytics dashboard.
 * Uses Java records concept — immutable data carrier.
 * Builder pattern for clean construction in the service.
 */
@Getter
@Builder
public class OrganizerDashboardResponse {
    private Long organizerId;
    private String organizerName;
    private BigDecimal totalRevenue;
    private int totalEvents;
    private int totalBookings;
    private List<EventRevenueDto> events;

    @Getter
    @Builder
    public static class EventRevenueDto {
        private Long eventId;
        private String eventTitle;
        private String eventDate;
        private String status;
        private int totalBookings;
        private BigDecimal revenue;
        private BigDecimal runningTotal;
    }
}