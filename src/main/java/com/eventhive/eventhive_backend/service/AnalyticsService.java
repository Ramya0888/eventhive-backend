package com.eventhive.eventhive_backend.service;

import com.eventhive.eventhive_backend.dto.analytics.AdminDashboardResponse;
import com.eventhive.eventhive_backend.dto.analytics.OrganizerDashboardResponse;
import com.eventhive.eventhive_backend.entity.User;
import com.eventhive.eventhive_backend.repository.BookingRepository;
import com.eventhive.eventhive_backend.repository.EventRepository;
import com.eventhive.eventhive_backend.repository.PaymentRepository;
import com.eventhive.eventhive_backend.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AnalyticsService {

    private final EventRepository eventRepository;
    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;

    public AnalyticsService(EventRepository eventRepository,
                            BookingRepository bookingRepository,
                            PaymentRepository paymentRepository,
                            UserRepository userRepository) {
        this.eventRepository = eventRepository;
        this.bookingRepository = bookingRepository;
        this.paymentRepository = paymentRepository;
        this.userRepository = userRepository;
    }

   
    @Transactional(readOnly = true)
    public OrganizerDashboardResponse getOrganizerDashboard(User organizer) {
        log.info("Building organizer dashboard for user {}", organizer.getId());

        List<Object[]> rows = eventRepository
                .getOrganizerEventRevenue(organizer.getId());

        // Map each Object[] row to a typed DTO.
        // Column order matches the SELECT in the query:
        // [0]=event_id, [1]=event_title, [2]=event_date,
        // [3]=status, [4]=total_bookings, [5]=revenue, [6]=running_total
        List<OrganizerDashboardResponse.EventRevenueDto> eventDtos = rows.stream()
                .map(row -> OrganizerDashboardResponse.EventRevenueDto.builder()
                        .eventId(((Number) row[0]).longValue())
                        .eventTitle((String) row[1])
                        .eventDate(row[2] != null ? row[2].toString() : null)
                        .status((String) row[3])
                        .totalBookings(((Number) row[4]).intValue())
                        .revenue(new BigDecimal(row[5].toString()))
                        .runningTotal(new BigDecimal(row[6].toString()))
                        .build())
                .collect(Collectors.toList());

        // Aggregate totals from the event rows using Streams.
        BigDecimal totalRevenue = eventDtos.stream()
                .map(OrganizerDashboardResponse.EventRevenueDto::getRevenue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        int totalBookings = eventDtos.stream()
                .mapToInt(OrganizerDashboardResponse.EventRevenueDto::getTotalBookings)
                .sum();

        return OrganizerDashboardResponse.builder()
                .organizerId(organizer.getId())
                .organizerName(organizer.getName())
                .totalRevenue(totalRevenue)
                .totalEvents(eventDtos.size())
                .totalBookings(totalBookings)
                .events(eventDtos)
                .build();
    }

    /**
     * ADMIN DASHBOARD — platform-wide stats.
     * Combines simple counts with window-function queries for trends.
     */
    @Transactional(readOnly = true)
    public AdminDashboardResponse getAdminDashboard() {
        log.info("Building admin dashboard");

        // Simple counts — derived query methods from repositories
        long totalEvents    = eventRepository.count();
        long totalUsers     = userRepository.count();
        long totalBookings  = bookingRepository.count();

        // Monthly revenue with LAG() for growth calculation.
        // [0]=month, [1]=payment_count, [2]=monthly_revenue,
        // [3]=prev_month_revenue (null for first month)
        List<Object[]> monthlyRows = eventRepository.getMonthlyRevenue();

        List<AdminDashboardResponse.MonthlyRevenueDto> monthlyDtos =
                monthlyRows.stream().map(row -> {
                    BigDecimal current  = new BigDecimal(row[2].toString());
                    BigDecimal previous = row[3] != null
                            ? new BigDecimal(row[3].toString()) : null;

                    // Growth % = ((current - previous) / previous) * 100
                    // Only calculable when previous month exists and is non-zero.
                    Double growth = null;
                    if (previous != null && previous.compareTo(BigDecimal.ZERO) != 0) {
                        growth = current.subtract(previous)
                                .divide(previous, 4, RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(100))
                                .doubleValue();
                    }

                    return AdminDashboardResponse.MonthlyRevenueDto.builder()
                            .month((String) row[0])
                            .paymentCount(((Number) row[1]).intValue())
                            .monthlyRevenue(current)
                            .prevMonthRevenue(previous)
                            .growthPercent(growth)
                            .build();
                }).collect(Collectors.toList());

        // Total platform revenue = sum of all monthly revenues
        BigDecimal totalRevenue = monthlyDtos.stream()
                .map(AdminDashboardResponse.MonthlyRevenueDto::getMonthlyRevenue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Top organizers with RANK() window function.
        // [0]=organizer_id, [1]=name, [2]=total_events,
        // [3]=total_bookings, [4]=total_revenue, [5]=rank
        List<Object[]> topOrgRows = eventRepository.getTopOrganizers();

        List<AdminDashboardResponse.TopOrganizerDto> topOrgs = topOrgRows.stream()
                .map(row -> AdminDashboardResponse.TopOrganizerDto.builder()
                        .organizerId(((Number) row[0]).longValue())
                        .organizerName((String) row[1])
                        .totalEvents(((Number) row[2]).intValue())
                        .totalBookings(((Number) row[3]).intValue())
                        .totalRevenue(new BigDecimal(row[4].toString()))
                        .revenueRank(((Number) row[5]).intValue())
                        .build())
                .collect(Collectors.toList());

        return AdminDashboardResponse.builder()
                .totalPlatformRevenue(totalRevenue)
                .totalEvents(totalEvents)
                .totalUsers(totalUsers)
                .totalBookings(totalBookings)
                .monthlyRevenue(monthlyDtos)
                .topOrganizers(topOrgs)
                .build();
    }
}