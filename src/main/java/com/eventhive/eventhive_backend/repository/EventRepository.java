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

    // Find all events created by this organizer — for organizer's own dashboard.
    // Derived query: Spring parses method name into WHERE organizer_id = ?
    List<Event> findByOrganizerId(Long organizerId);

    // All events of a given status, sorted by event date ascending.
    // Used by the cleanup scheduler and pending-approval list.
    List<Event> findByStatusOrderByEventDateAsc(EventStatus status);

    // Events sorted by creation time — used for admin pending approvals list.
    List<Event> findByStatusOrderByCreatedAtAsc(EventStatus status);

    /**
     * Paginated, filtered catalog query (JPQL).
     *
     * Each filter is OPTIONAL via ":param IS NULL OR ..." idiom —
     * when a param is null the condition short-circuits to true,
     * effectively skipping that filter.
     *
     * AND e.eventDate >= CURRENT_DATE: ensures past events never
     * appear in the catalog even if they are still PUBLISHED.
     * The @Scheduled job moves them to COMPLETED overnight, but
     * this filter is a safety net that works immediately.
     */
    @Query("""
            SELECT e FROM Event e
            WHERE e.status = :status
              AND e.eventDate >= CURRENT_DATE
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
     * nativeQuery = true: raw MySQL — JPQL has no MATCH AGAINST syntax.
     * Relies on FULLTEXT index idx_events_search(title, description).
     *
     * AND e.event_date >= CURDATE(): same past-event filter as searchEvents.
     *
     * countQuery: Spring cannot auto-derive COUNT for native pageable queries
     * so we supply one with the same WHERE clause.
     *
     * NOTE: No SQL -- comments inside the query string — Spring Data's
     * query parser misinterprets them with '%Y-%m' format strings.
     */
    @Query(
        value = """
            SELECT e.* FROM events e
            LEFT JOIN venues v ON e.venue_id = v.id
            WHERE e.status = 'PUBLISHED'
              AND e.event_date >= CURDATE()
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
              AND e.event_date >= CURDATE()
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
     * ORGANIZER REVENUE SUMMARY using CTE (Common Table Expression).
     *
     * A CTE (WITH clause) names a temporary result set scoped to this
     * query — like a variable for SQL. Makes complex multi-step queries
     * readable by breaking them into named stages.
     *
     * SUM(revenue) OVER (ORDER BY event_date): window function that
     * accumulates revenue across rows sorted by date — each row shows
     * total revenue up to and including that event (running total).
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
                SUM(revenue) OVER (ORDER BY event_date) AS running_total
            FROM event_revenue
            ORDER BY event_date
            """,
            nativeQuery = true)
    List<Object[]> getOrganizerEventRevenue(@Param("organizerId") Long organizerId);

    /**
     * PLATFORM MONTHLY REVENUE using LAG() window function.
     *
     * LAG(column, 1) returns the value from the previous row in the
     * window's ORDER BY sequence — previous month's revenue — so we
     * can compute month-over-month growth without a self-join.
     *
     * NOTE: No SQL -- comments inside the query string (parser issue
     * with '%Y-%m' single quotes and comment characters together).
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
     * TOP ORGANIZERS by revenue using RANK() window function.
     *
     * RANK() assigns a rank to each organizer by total revenue.
     * RANK() vs ROW_NUMBER(): RANK() gives tied organizers the same
     * rank (1,1,3), ROW_NUMBER() always increments (1,2,3).
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
     *
     * nativeQuery = true: CALL is MySQL syntax, not expressible in JPQL.
     * Alternative: @NamedStoredProcedureQuery on the entity — better for
     * procedures called in many places; @Query is simpler for one-off calls.
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
