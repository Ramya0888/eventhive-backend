package com.eventhive.eventhive_backend.controller;

import com.eventhive.eventhive_backend.dto.ApiResponse;
import com.eventhive.eventhive_backend.dto.analytics.AdminDashboardResponse;
import com.eventhive.eventhive_backend.dto.analytics.OrganizerDashboardResponse;
import com.eventhive.eventhive_backend.security.CustomUserDetails;
import com.eventhive.eventhive_backend.service.AnalyticsService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import com.eventhive.eventhive_backend.repository.EventRepository;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final EventRepository eventRepository;

    public AnalyticsController(AnalyticsService analyticsService, EventRepository eventRepository) {
        this.analyticsService = analyticsService;
        this.eventRepository = eventRepository;
    }

    /**
     * GET /api/analytics/organizer
     * Organizer sees their own events' revenue and booking breakdown.
     
     */
    @GetMapping("/organizer")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<ApiResponse<OrganizerDashboardResponse>> getOrganizerDashboard(
            @AuthenticationPrincipal CustomUserDetails principal) {

        OrganizerDashboardResponse dashboard =
                analyticsService.getOrganizerDashboard(principal.getUser());
        return ResponseEntity.ok(
                ApiResponse.success("Organizer dashboard fetched", dashboard));
    }

    /**
     * GET /api/analytics/admin
     * Platform-wide stats — admin only.
     */
    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AdminDashboardResponse>> getAdminDashboard() {

        AdminDashboardResponse dashboard = analyticsService.getAdminDashboard();
        return ResponseEntity.ok(
                ApiResponse.success("Admin dashboard fetched", dashboard));
    }

    /**
     * GET /api/analytics/organizer/report?fromDate=2026-01-01&toDate=2026-12-31
     * Calls the stored procedure for a custom date range.
     */
    @GetMapping("/organizer/report")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<ApiResponse<List<Object[]>>> getOrganizerReport(
            @RequestParam String fromDate,
            @RequestParam String toDate,
            @AuthenticationPrincipal CustomUserDetails principal) {

        List<Object[]> result = eventRepository.callOrganizerRevenueProc(
                principal.getUser().getId(), fromDate, toDate);
        return ResponseEntity.ok(
                ApiResponse.success("Report generated", result));
    }
}