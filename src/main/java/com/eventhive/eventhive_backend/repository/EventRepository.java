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
}