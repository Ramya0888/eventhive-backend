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
import com.eventhive.eventhive_backend.exception.InvalidEventStateException;
import com.eventhive.eventhive_backend.exception.ResourceNotFoundException;
import com.eventhive.eventhive_backend.repository.CategoryRepository;
import com.eventhive.eventhive_backend.repository.EventRepository;
import com.eventhive.eventhive_backend.repository.SeatCategoryRepository;
import com.eventhive.eventhive_backend.repository.SeatRepository;
import com.eventhive.eventhive_backend.repository.VenueRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

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

    /**
     * CREATE EVENT — atomic write across 4 tables.
     *
     * @Transactional: venue + event + seat categories + seats all commit
     * together. If any insert fails, all four roll back — ACID atomicity.
     * A partial failure can never leave an event with missing seats.
     */
    @Transactional
    public EventResponse createEvent(CreateEventRequest request, User organizer) {
        log.info("Organizer {} creating event '{}'", organizer.getId(), request.getTitle());

        // 1. Validate category exists (FK integrity at the app layer too)
        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Category not found: " + request.getCategoryId()));

        // 2. Derive total capacity by summing seat category counts
        int totalCapacity = request.getSeatCategories().stream()
                .mapToInt(CreateEventRequest.SeatCategoryRequest::getCount)
                .sum();

        // 3. Create venue (write #1)
        Venue venue = Venue.builder()
                .name(request.getVenue().getName())
                .address(request.getVenue().getAddress())
                .city(request.getVenue().getCity())
                .totalCapacity(totalCapacity)
                .build();
        venue = venueRepository.save(venue);

        // 4. Create the event in DRAFT status (write #2)
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

        // 5. Generate seat categories and individual seat rows (writes #3 and #4)
        for (CreateEventRequest.SeatCategoryRequest catReq : request.getSeatCategories()) {
            SeatCategory seatCategory = SeatCategory.builder()
                    .event(event)
                    .name(catReq.getName())
                    .price(catReq.getPrice())
                    .totalCount(catReq.getCount())
                    .build();
            seatCategory = seatCategoryRepository.save(seatCategory);

            // Padded seat numbers: VIP-001, VIP-002, GEN-042
            for (int i = 1; i <= catReq.getCount(); i++) {
                String prefix = catReq.getName().toUpperCase()
                        .substring(0, Math.min(3, catReq.getName().length()));
                String seatNumber = prefix + "-" + String.format("%03d", i);
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

    /**
     * PUBLIC CATALOG — PUBLISHED events with future dates only.
     * Now replaced by searchPublishedEvents with pagination.
     * Kept for backward compatibility.
     */
    @Transactional(readOnly = true)
    public List<EventResponse> getPublishedEvents() {
        return eventRepository.findByStatusOrderByEventDateAsc(EventStatus.PUBLISHED)
                .stream()
                .map(EventResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * SINGLE EVENT BY ID — with visibility rules.
     *
     * PUBLISHED and ONGOING events: visible to everyone (including anonymous).
     * ONGOING included so attendees can view an event happening today.
     *
     * Non-public statuses (DRAFT, PENDING_APPROVAL, CANCELLED, COMPLETED):
     * only the owning organizer or an ADMIN can see them.
     * Everyone else gets 404 — we don't reveal the event exists.
     * This prevents id-enumeration of private drafts (IDOR prevention).
     *
     * @param requester the logged-in user, or null if the caller is anonymous
     */
    @Transactional(readOnly = true)
    public EventResponse getEventById(Long id, User requester) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + id));

        // PUBLISHED or ONGOING — publicly visible
        if (event.getStatus() == EventStatus.PUBLISHED
                || event.getStatus() == EventStatus.ONGOING) {
            return EventResponse.from(event);
        }

        // Not public — only owner or admin may view
        boolean isOwner = requester != null
                && event.getOrganizer().getId().equals(requester.getId());
        boolean isAdmin = requester != null && hasRole(requester, "ADMIN");

        if (isOwner || isAdmin) {
            return EventResponse.from(event);
        }

        // Non-disclosure: return 404 rather than 403
        throw new ResourceNotFoundException("Event not found: " + id);
    }

    // Checks if a user holds a given role name.
    private boolean hasRole(User user, String roleName) {
        return user.getRoles().stream()
                .anyMatch(r -> r.getRoleName().equals(roleName));
    }

    /**
     * ORGANIZER'S OWN EVENTS — includes DRAFTs (unlike the public catalog).
     * readOnly = true: no writes, Hibernate skips dirty-checking.
     */
    @Transactional(readOnly = true)
    public List<EventResponse> getMyEvents(User organizer) {
        return eventRepository.findByOrganizerId(organizer.getId())
                .stream()
                .map(EventResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * SUBMIT FOR APPROVAL — organizer moves DRAFT to PENDING_APPROVAL.
     *
     * State guard: only DRAFT events can be submitted.
     * Ownership guard: only the owning organizer can submit (via getOwnedEvent).
     * No save() needed — managed entity + @Transactional → dirty checking flushes
     * the status change on commit. This is a common interview question.
     */
    @Transactional
    public EventResponse submitForApproval(Long eventId, User organizer) {
        Event event = getOwnedEvent(eventId, organizer);

        if (event.getStatus() != EventStatus.DRAFT) {
            throw new InvalidEventStateException(
                    "Only DRAFT events can be submitted. Current status: " + event.getStatus());
        }
        event.setStatus(EventStatus.PENDING_APPROVAL);
        return EventResponse.from(event);
    }

    /**
     * APPROVE — admin moves PENDING_APPROVAL to PUBLISHED.
     * Once PUBLISHED, the event appears in the public catalog.
     */
    @Transactional
    public EventResponse approveEvent(Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + eventId));

        if (event.getStatus() != EventStatus.PENDING_APPROVAL) {
            throw new InvalidEventStateException(
                    "Only PENDING_APPROVAL events can be approved. Current status: "
                    + event.getStatus());
        }
        event.setStatus(EventStatus.PUBLISHED);
        return EventResponse.from(event);
    }

    /**
     * REJECT — admin sends PENDING_APPROVAL back to DRAFT.
     * Organizer can fix and resubmit.
     */
    @Transactional
    public EventResponse rejectEvent(Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + eventId));

        if (event.getStatus() != EventStatus.PENDING_APPROVAL) {
            throw new InvalidEventStateException(
                    "Only PENDING_APPROVAL events can be rejected. Current status: "
                    + event.getStatus());
        }
        event.setStatus(EventStatus.DRAFT);
        return EventResponse.from(event);
    }

    /**
     * CANCEL — organizer cancels their own event.
     * Cannot cancel an event already COMPLETED or CANCELLED.
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
     * UPDATE EVENT — organizer edits a DRAFT event (PUT-style full replacement).
     *
     * PUT semantics: client sends all editable fields; every field replaces
     * the current value. Editing is only allowed in DRAFT — once submitted
     * or published, the event details are locked (attendees may have acted on them).
     */
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

        // Overwrite all editable fields — dirty checking flushes as one UPDATE.
        event.setTitle(request.getTitle());
        event.setDescription(request.getDescription());
        event.setCategory(category);
        event.setEventDate(request.getEventDate());
        event.setStartTime(request.getStartTime());
        event.setEndTime(request.getEndTime());

        return EventResponse.from(event);
    }

    /**
     * ADMIN — get all events pending approval.
     */
    @Transactional(readOnly = true)
    public List<EventResponse> getPendingEvents() {
        return eventRepository.findByStatusOrderByCreatedAtAsc(EventStatus.PENDING_APPROVAL)
                .stream()
                .map(EventResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * PAGINATED, FILTERED CATALOG — powers GET /api/events.
     *
     * Branches on keyword:
     * - With keyword → FULLTEXT native query ranked by relevance
     * - Without keyword → JPQL filter query sorted by date
     *
     * Page size is clamped server-side (max 50) — prevents a client
     * from requesting ?size=1000000 and DoS-ing the database.
     */
    @Transactional(readOnly = true)
    public PagedResponse<EventResponse> searchPublishedEvents(
            String keyword, Long categoryId, String city,
            LocalDate fromDate, LocalDate toDate, int page, int size) {

        int safeSize = Math.min(Math.max(size, 1), 50);
        int safePage = Math.max(page, 0);

        Page<Event> result;

        if (keyword != null && !keyword.isBlank()) {
            // Keyword path: no Sort in Pageable — query owns ORDER BY relevance DESC
            Pageable pageable = PageRequest.of(safePage, safeSize);
            result = eventRepository.searchEventsByKeyword(
                    keyword.trim(), categoryId, city, fromDate, toDate, pageable);
        } else {
            // Filter path: sorted by date ascending (soonest events first)
            Pageable pageable = PageRequest.of(safePage, safeSize,
                    Sort.by("eventDate").ascending());
            result = eventRepository.searchEvents(
                    EventStatus.PUBLISHED, categoryId, city, fromDate, toDate, pageable);
        }

        return PagedResponse.from(result, EventResponse::from);
    }

    /**
     * SCHEDULED STATUS UPDATE — runs at midnight every day.
     *
     * Two transitions:
     * PUBLISHED/ONGOING → COMPLETED : event_date is before today
     * PUBLISHED         → ONGOING   : event starts today and startTime has passed
     *
     * cron = "0 0 0 * * *": second=0, minute=0, hour=0, every day.
     *
     * For testing: temporarily change to @Scheduled(fixedDelay = 30_000)
     * to run every 30 seconds without waiting until midnight.
     *
     * No save() calls needed — managed entities inside @Transactional
     * are dirty-checked and flushed automatically on commit.
     */
    @Scheduled(fixedDelay = 900_000)
    @Transactional
    public void updateEventStatuses() {
        LocalDate today = LocalDate.now();
        LocalTime now   = LocalTime.now();

        log.info("Running event status update job — date: {}", today);

        // PUBLISHED/ONGOING → COMPLETED: event date is in the past.
        // Uses a repository query to avoid loading all events into memory.
        List<Event> toCompleted = eventRepository.findAll().stream()
                .filter(e -> (e.getStatus() == EventStatus.PUBLISHED
                             || e.getStatus() == EventStatus.ONGOING)
                        && e.getEventDate().isBefore(today))
                .collect(Collectors.toList());

        toCompleted.forEach(e -> {
            e.setStatus(EventStatus.COMPLETED);
            log.info("Event {} '{}' moved to COMPLETED", e.getId(), e.getTitle());
        });

        // PUBLISHED → ONGOING: event starts today and startTime has passed.
        List<Event> toOngoing = eventRepository
                .findByStatusOrderByEventDateAsc(EventStatus.PUBLISHED)
                .stream()
                .filter(e -> e.getEventDate().equals(today)
                        && e.getStartTime() != null
                        && now.isAfter(e.getStartTime()))
                .collect(Collectors.toList());

        toOngoing.forEach(e -> {
            e.setStatus(EventStatus.ONGOING);
            log.info("Event {} '{}' moved to ONGOING", e.getId(), e.getTitle());
        });

        log.info("Status update complete — {} completed, {} ongoing",
                toCompleted.size(), toOngoing.size());
    }

    /**
     * Private helper — fetches an event and verifies the given organizer owns it.
     * Returns 404 (not 403) for non-owners — non-disclosure principle:
     * don't reveal an event exists to someone who can't act on it.
     */
    private Event getOwnedEvent(Long eventId, User organizer) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Event not found: " + eventId));

        if (!event.getOrganizer().getId().equals(organizer.getId())) {
            throw new ResourceNotFoundException("Event not found: " + eventId);
        }
        return event;
    }
}
