package com.eventhive.eventhive_backend.repository;

import com.eventhive.eventhive_backend.entity.Event;
import com.eventhive.eventhive_backend.enums.EventStatus;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;


import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

public interface EventRepository extends JpaRepository<Event, Long> {

    // "Find all events created by this organizer" — for the organizer's own
    // dashboard. 
    List<Event> findByOrganizerId(Long organizerId);
    // Attendee catalog: only PUBLISHED events, soonest first.
    // This is the query the (status, event_date) composite index was built for.
    List<Event> findByStatusOrderByEventDateAsc(EventStatus status);
    List<Event> findByStatusOrderByCreatedAtAsc(EventStatus status);
 
    @Query("""
            SELECT e FROM Event e
            WHERE e.status = :status
              AND (:categoryId IS NULL OR e.category.id = :categoryId)
              AND (:city IS NULL OR e.venue.city = :city)
              AND (:fromDate IS NULL OR e.eventDate >= :fromDate)
              AND (:toDate IS NULL OR e.eventDate <= :toDate)
            """)
    Page<Event> searchEvents(
            @Param("status") EventStatus status,
            @Param("categoryId") Long categoryId,
            @Param("city") String city,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            Pageable pageable);

            /**
     * Full-text keyword search over title + description, ranked by relevance.
     *
     * nativeQuery = true: this is raw MySQL, NOT JPQL — JPQL has no MATCH AGAINST.
     * We reference the real table ('events') and columns, and rely on the
     * FULLTEXT index idx_events_search(title, description).
     *
     * Optional filters (category/city/dates) use the same ":param IS NULL OR ..."
     * idiom. Results are ORDER BY relevance DESC — the score MATCH AGAINST returns.
     *
     * countQuery: with native pagination, Spring can't auto-derive the COUNT,
     * so we supply our own matching the same WHERE.
     */
    @Query(
        value = """
            SELECT e.* FROM events e
            LEFT JOIN venues v ON e.venue_id = v.id
            WHERE e.status = 'PUBLISHED'
              AND MATCH(e.title, e.description) AGAINST (:keyword IN NATURAL LANGUAGE MODE)
              AND (:categoryId IS NULL OR e.category_id = :categoryId)
              AND (:city IS NULL OR v.city = :city)
              AND (:fromDate IS NULL OR e.event_date >= :fromDate)
              AND (:toDate IS NULL OR e.event_date <= :toDate)
            ORDER BY MATCH(e.title, e.description) AGAINST (:keyword IN NATURAL LANGUAGE MODE) DESC
            """,
        countQuery = """
            SELECT COUNT(*) FROM events e
            LEFT JOIN venues v ON e.venue_id = v.id
            WHERE e.status = 'PUBLISHED'
              AND MATCH(e.title, e.description) AGAINST (:keyword IN NATURAL LANGUAGE MODE)
              AND (:categoryId IS NULL OR e.category_id = :categoryId)
              AND (:city IS NULL OR v.city = :city)
              AND (:fromDate IS NULL OR e.event_date >= :fromDate)
              AND (:toDate IS NULL OR e.event_date <= :toDate)
            """,
        nativeQuery = true
    )
    
    Page<Event> searchEventsByKeyword(
            @Param("keyword") String keyword,
            @Param("categoryId") Long categoryId,
            @Param("city") String city,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            Pageable pageable);

            /**
             * ORGANIZER REVENUE SUMMARY — using CTE (Common Table Expression).
             
             */
            @Query(value = """
                    WITH event_revenue AS (
                        SELECT
                            e.id           AS event_id,
                            e.title        AS event_title,
                            e.event_date,
                            e.status,
                            COUNT(DISTINCT b.id)           AS total_bookings,
                            COALESCE(SUM(pay.amount), 0)   AS revenue
                        FROM events e
                        LEFT JOIN bookings b
                            ON b.event_id = e.id AND b.status = 'CONFIRMED'
                        LEFT JOIN payments pay
                            ON pay.booking_id = b.id AND pay.status = 'SUCCESS'
                        WHERE e.organizer_id = :organizerId
                        GROUP BY e.id, e.title, e.event_date, e.status
                    )
                    SELECT
                        event_id,
                        event_title,
                        event_date,
                        status,
                        total_bookings,
                        revenue,
                        -- Running total: SUM() OVER ORDER BY accumulates revenue
                        -- across rows sorted by date. Each row shows total revenue
                        -- up to and including that event.
                        SUM(revenue) OVER (ORDER BY event_date) AS running_total
                    FROM event_revenue
                    ORDER BY event_date
                    """,
                    nativeQuery = true)
            List<Object[]> getOrganizerEventRevenue(@Param("organizerId") Long organizerId);
        
            /**
             * PLATFORM MONTHLY REVENUE — using LAG() window function.
             
             */
            @Query(value = """
                    SELECT
                        DATE_FORMAT(p.created_at, '%Y-%m')          AS month,
                        COUNT(DISTINCT p.id)                         AS payment_count,
                        SUM(p.amount)                                AS monthly_revenue,
                      
                        LAG(SUM(p.amount), 1) OVER (
                            ORDER BY DATE_FORMAT(p.created_at, '%Y-%m')
                        )                                            AS prev_month_revenue
                    FROM payments p
                    WHERE p.status = 'SUCCESS'
                    GROUP BY DATE_FORMAT(p.created_at, '%Y-%m')
                    ORDER BY month
                    """,
                    nativeQuery = true)
            List<Object[]> getMonthlyRevenue();
        
            /**
             * TOP ORGANIZERS by revenue — using RANK() window function.
             */
            @Query(value = """
                    SELECT
                        u.id                                AS organizer_id,
                        u.name                              AS organizer_name,
                        COUNT(DISTINCT e.id)                AS total_events,
                        COUNT(DISTINCT b.id)                AS total_bookings,
                        COALESCE(SUM(p.amount), 0)          AS total_revenue,
                        RANK() OVER (
                            ORDER BY COALESCE(SUM(p.amount), 0) DESC
                        )                                   AS revenue_rank
                    FROM users u
                    JOIN events e       ON e.organizer_id = u.id
                    LEFT JOIN bookings b
                        ON b.event_id = e.id AND b.status = 'CONFIRMED'
                    LEFT JOIN payments p
                        ON p.booking_id = b.id AND p.status = 'SUCCESS'
                    JOIN user_roles ur  ON ur.user_id = u.id
                    JOIN roles r        ON r.id = ur.role_id
                        AND r.role_name = 'ORGANIZER'
                    GROUP BY u.id, u.name
                    ORDER BY total_revenue DESC
                    LIMIT 10
                    """,
                    nativeQuery = true)
            List<Object[]> getTopOrganizers();

            /**
     * Calls the sp_organizer_revenue stored procedure.
     * Native query: CALL is MySQL syntax, not expressible in JPQL.
     *
    
     */
    @Query(value = """
            CALL sp_organizer_revenue(:organizerId, :fromDate, :toDate)
            """,
            nativeQuery = true)
    List<Object[]> callOrganizerRevenueProc(
            @Param("organizerId") Long organizerId,
            @Param("fromDate") String fromDate,
            @Param("toDate") String toDate);
}