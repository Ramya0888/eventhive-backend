package com.eventhive.eventhive_backend.dto.analytics;

import lombok.Builder;
import lombok.Getter;
import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
public class AdminDashboardResponse {
    private BigDecimal totalPlatformRevenue;
    private long totalEvents;
    private long totalUsers;
    private long totalBookings;
    private List<MonthlyRevenueDto> monthlyRevenue;
    private List<TopOrganizerDto> topOrganizers;

    @Getter
    @Builder
    public static class MonthlyRevenueDto {
        private String month;
        private int paymentCount;
        private BigDecimal monthlyRevenue;
        private BigDecimal prevMonthRevenue;   // null for first month
        private Double growthPercent;          // null for first month
    }

    @Getter
    @Builder
    public static class TopOrganizerDto {
        private Long organizerId;
        private String organizerName;
        private int totalEvents;
        private int totalBookings;
        private BigDecimal totalRevenue;
        private int revenueRank;
    }
}