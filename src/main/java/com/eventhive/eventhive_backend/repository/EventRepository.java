package com.eventhive.eventhive_backend.repository;

import com.eventhive.eventhive_backend.entity.Event;
import com.eventhive.eventhive_backend.enums.EventStatus;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EventRepository extends JpaRepository<Event, Long> {

    // "Find all events created by this organizer" — for the organizer's own
    // dashboard. 
    List<Event> findByOrganizerId(Long organizerId);
    // Attendee catalog: only PUBLISHED events, soonest first.
    // This is the query the (status, event_date) composite index was built for.
    List<Event> findByStatusOrderByEventDateAsc(EventStatus status);
}