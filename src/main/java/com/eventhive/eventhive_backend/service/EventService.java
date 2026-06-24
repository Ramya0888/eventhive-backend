package com.eventhive.eventhive_backend.service;

import com.eventhive.eventhive_backend.dto.CreateEventRequest;
import com.eventhive.eventhive_backend.dto.EventResponse;
import com.eventhive.eventhive_backend.dto.PagedResponse;
import com.eventhive.eventhive_backend.dto.UpdateEventRequest;
import com.eventhive.eventhive_backend.entity.Category;
import com.eventhive.eventhive_backend.entity.Event;
import com.eventhive.eventhive_backend.entity.Seat;
import com.eventhive.eventhive_backend.entity.SeatCategory;
import com.eventhive.eventhive_backend.entity.User;
import com.eventhive.eventhive_backend.entity.Venue;
import com.eventhive.eventhive_backend.enums.EventStatus;
import com.eventhive.eventhive_backend.enums.SeatStatus;
import com.eventhive.eventhive_backend.exception.ResourceNotFoundException;
import com.eventhive.eventhive_backend.repository.CategoryRepository;
import com.eventhive.eventhive_backend.repository.EventRepository;
import com.eventhive.eventhive_backend.repository.VenueRepository;
import com.eventhive.eventhive_backend.repository.SeatCategoryRepository;
import com.eventhive.eventhive_backend.repository.SeatRepository;
import lombok.extern.slf4j.Slf4j;
import com.eventhive.eventhive_backend.exception.InvalidEventStateException;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import com.eventhive.eventhive_backend.dto.PagedResponse;
import java.time.LocalDate;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
public class EventService {

    private final EventRepository eventRepository;
    private final VenueRepository venueRepository;
    private final CategoryRepository categoryRepository;
    private final SeatCategoryRepository seatCategoryRepository;
    private final SeatRepository seatRepository;

    public EventService(EventRepository eventRepository,
                        VenueRepository venueRepository,
                        CategoryRepository categoryRepository,
                        SeatCategoryRepository seatCategoryRepository,
                        SeatRepository seatRepository) {
        this.eventRepository = eventRepository;
        this.venueRepository = venueRepository;
        this.categoryRepository = categoryRepository;
        this.seatCategoryRepository = seatCategoryRepository;
        this.seatRepository = seatRepository;
    }

   
@Transactional
    public EventResponse createEvent(CreateEventRequest request, User organizer) {
        log.info("Organizer {} creating event '{}'", organizer.getId(), request.getTitle());

        // 1. Validate category
        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Category not found: " + request.getCategoryId()));

        // 2. Derive total capacity from seat category counts
        int totalCapacity = request.getSeatCategories().stream()
                .mapToInt(CreateEventRequest.SeatCategoryRequest::getCount)
                .sum();

        // 3. Create venue
        Venue venue = Venue.builder()
                .name(request.getVenue().getName())
                .address(request.getVenue().getAddress())
                .city(request.getVenue().getCity())
                .totalCapacity(totalCapacity)
                .build();
        venue = venueRepository.save(venue);

        // 4. Create the event
        Event event = Event.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .status(EventStatus.DRAFT)
                .eventDate(request.getEventDate())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .availableSeats(totalCapacity)
                .organizer(organizer)
                .category(category)
                .venue(venue)
                .build();
        event = eventRepository.save(event);

        // 5. Generate seat categories and individual seats.
        // All three writes (event + categories + seats) are in one @Transactional —
        // if seat generation fails, the event and venue inserts roll back too.
        // This is ACID atomicity across three tables.
        for (CreateEventRequest.SeatCategoryRequest catReq : request.getSeatCategories()) {
            SeatCategory seatCategory = SeatCategory.builder()
                    .event(event)
                    .name(catReq.getName())
                    .price(catReq.getPrice())
                    .totalCount(catReq.getCount())
                    .build();
            seatCategory = seatCategoryRepository.save(seatCategory);

            // Generate individual seat rows with padded seat numbers
            // e.g. VIP-001, VIP-002, ... GEN-042
            for (int i = 1; i <= catReq.getCount(); i++) {
                String seatNumber = catReq.getName().toUpperCase().substring(0, Math.min(3, catReq.getName().length()))
                        + "-" + String.format("%03d", i);
                Seat seat = Seat.builder()
                        .event(event)
                        .seatCategory(seatCategory)
                        .seatNumber(seatNumber)
                        .status(SeatStatus.AVAILABLE)
                        .build();
                seatRepository.save(seat);
            }
        }

        log.info("Event {} created with {} seats across {} categories",
                event.getId(), totalCapacity, request.getSeatCategories().size());

        return EventResponse.from(event);
    }

  
    @Transactional(readOnly = true)
    public List<EventResponse> getPublishedEvents() {
        return eventRepository.findByStatusOrderByEventDateAsc(EventStatus.PUBLISHED)
                .stream()
                .map(EventResponse::from)   // method reference — maps each entity to a DTO
                .collect(Collectors.toList());
    }

    /**
     * Single event by id, with visibility rules:
     *  - PUBLISHED events are visible to everyone (including anonymous users).
     *  - Non-published (e.g. DRAFT) events are visible ONLY to their owning
     *    organizer or an ADMIN. Everyone else gets 404 — we don't even reveal
     *    the event exists (prevents id-enumeration of private drafts).
     *
     * @param requester the logged-in user, or null if the caller is anonymous
     */
    @Transactional(readOnly = true)
    public EventResponse getEventById(Long id, User requester) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + id));

        if (event.getStatus() == EventStatus.PUBLISHED) {
            return EventResponse.from(event);   // public — anyone may view
        }

        // Not published: only the owner or an admin may view it.
        boolean isOwner = requester != null
                && event.getOrganizer().getId().equals(requester.getId());
        boolean isAdmin = requester != null && hasRole(requester, "ADMIN");

        if (isOwner || isAdmin) {
            return EventResponse.from(event);
        }

        // Anyone else: behave as if it doesn't exist.
        throw new ResourceNotFoundException("Event not found: " + id);
    }

    // Small helper — checks if a user holds a given role name.
    private boolean hasRole(User user, String roleName) {
        return user.getRoles().stream()
                .anyMatch(r -> r.getRoleName().equals(roleName));
    }

   
    @Transactional(readOnly = true)
    public List<EventResponse> getMyEvents(User organizer) {
        return eventRepository.findByOrganizerId(organizer.getId())
                .stream()
                .map(EventResponse::from)
                .collect(Collectors.toList());
    }
    /**
     * ORGANIZER submits their DRAFT for admin review.
     * Legal only from DRAFT. Owner-only.
     */
    @Transactional
    public EventResponse submitForApproval(Long eventId, User organizer) {
        Event event = getOwnedEvent(eventId, organizer);

        if (event.getStatus() != EventStatus.DRAFT) {
            throw new InvalidEventStateException(
                    "Only DRAFT events can be submitted. Current status: " + event.getStatus());
        }
        event.setStatus(EventStatus.PENDING_APPROVAL);
        // No explicit save() needed: 'event' is a managed entity inside the
        // transaction, so Hibernate's dirty-checking flushes the change on commit.
        return EventResponse.from(event);
    }

    /**
     * ADMIN approves a pending event -> PUBLISHED (now visible in the catalog).
     * Legal only from PENDING_APPROVAL.
     */
    @Transactional
    public EventResponse approveEvent(Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + eventId));

        if (event.getStatus() != EventStatus.PENDING_APPROVAL) {
            throw new InvalidEventStateException(
                    "Only PENDING_APPROVAL events can be approved. Current status: " + event.getStatus());
        }
        event.setStatus(EventStatus.PUBLISHED);
        return EventResponse.from(event);
    }

    /**
     * ADMIN rejects a pending event -> back to DRAFT for the organizer to fix.
     */
    @Transactional
    public EventResponse rejectEvent(Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + eventId));

        if (event.getStatus() != EventStatus.PENDING_APPROVAL) {
            throw new InvalidEventStateException(
                    "Only PENDING_APPROVAL events can be rejected. Current status: " + event.getStatus());
        }
        event.setStatus(EventStatus.DRAFT);
        return EventResponse.from(event);
    }

    /**
     * ORGANIZER cancels their own event. Cannot cancel one already
     * COMPLETED or CANCELLED.
     */
    @Transactional
    public EventResponse cancelEvent(Long eventId, User organizer) {
        Event event = getOwnedEvent(eventId, organizer);

        if (event.getStatus() == EventStatus.COMPLETED
                || event.getStatus() == EventStatus.CANCELLED) {
            throw new InvalidEventStateException(
                    "Cannot cancel an event that is " + event.getStatus());
        }
        event.setStatus(EventStatus.CANCELLED);
        return EventResponse.from(event);
    }

    /**
     * Fetch an event and assert the given organizer owns it.
     * 404 if missing; 404 (not 403) if owned by someone else — same
     * non-disclosure rule as getEventById.
     */
    private Event getOwnedEvent(Long eventId, User organizer) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + eventId));

        if (!event.getOrganizer().getId().equals(organizer.getId())) {
            throw new ResourceNotFoundException("Event not found: " + eventId);
        }
        return event;
    }

  
    @Transactional
    public EventResponse updateEvent(Long eventId, UpdateEventRequest request, User organizer) {
        Event event = getOwnedEvent(eventId, organizer);

        if (event.getStatus() != EventStatus.DRAFT) {
            throw new InvalidEventStateException(
                    "Only DRAFT events can be edited. Current status: " + event.getStatus());
        }

        
        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Category not found: " + request.getCategoryId()));

        
       
        event.setTitle(request.getTitle());
        event.setDescription(request.getDescription());
        event.setCategory(category);
        event.setEventDate(request.getEventDate());
        event.setStartTime(request.getStartTime());
        event.setEndTime(request.getEndTime());

        return EventResponse.from(event);
    }

    @Transactional(readOnly = true)
    public PagedResponse<EventResponse> searchPublishedEvents(
            String keyword, Long categoryId, String city,
            LocalDate fromDate, LocalDate toDate, int page, int size) {

        int safeSize = Math.min(Math.max(size, 1), 50);
        int safePage = Math.max(page, 0);

        Page<Event> result;

        // Branch: keyword present -> relevance-ranked FULLTEXT query (no Sort —
        // ordering is by MATCH relevance inside the query itself).
        // keyword absent -> the JPQL filter query, sorted by date.
        if (keyword != null && !keyword.isBlank()) {
            Pageable pageable = PageRequest.of(safePage, safeSize); // no Sort: ranked by relevance
            result = eventRepository.searchEventsByKeyword(
                    keyword.trim(), categoryId, city, fromDate, toDate, pageable);
        } else {
            Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by("eventDate").ascending());
            result = eventRepository.searchEvents(
                    EventStatus.PUBLISHED, categoryId, city, fromDate, toDate, pageable);
        }

        return PagedResponse.from(result, EventResponse::from);
    }
    @Transactional(readOnly = true)
public List<EventResponse> getPendingEvents() {
    return eventRepository.findByStatusOrderByCreatedAtAsc(EventStatus.PENDING_APPROVAL)
            .stream()
            .map(EventResponse::from)
            .collect(Collectors.toList());
}
}