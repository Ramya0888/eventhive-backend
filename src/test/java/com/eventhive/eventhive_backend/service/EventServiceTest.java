package com.eventhive.eventhive_backend.service;

import com.eventhive.eventhive_backend.dto.EventResponse;
import com.eventhive.eventhive_backend.entity.Event;
import com.eventhive.eventhive_backend.entity.Role;
import com.eventhive.eventhive_backend.entity.User;
import com.eventhive.eventhive_backend.enums.EventStatus;
import com.eventhive.eventhive_backend.exception.ResourceNotFoundException;
import com.eventhive.eventhive_backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for EventService — focusing on IDOR (Insecure Direct Object
 * Reference) prevention in getEventById and getOwnedEvent.
 *
 * IDOR: an attacker guesses another user's resource ID and accesses it
 * directly. EventHive prevents this by returning 404 (not 403) for
 * non-owners — this avoids revealing that the event even exists.
 *
 * Interview Q: "Why 404 instead of 403 for unauthorized access?"
 * Answer: "403 tells the attacker the resource exists but they can't
 * access it — confirming the ID is valid. 404 reveals nothing. An
 * attacker enumerating event IDs gets no useful signal about which IDs
 * belong to other organizers. This is the non-disclosure principle."
 */
@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock private EventRepository eventRepository;
    @Mock private VenueRepository venueRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private SeatCategoryRepository seatCategoryRepository;
    @Mock private SeatRepository seatRepository;

    @InjectMocks
    private EventService eventService;

    private User ownerOrganizer;
    private User differentOrganizer;
    private User adminUser;
    private Event draftEvent;
    private Event publishedEvent;

    @BeforeEach
    void setUp() {
        // Organizer who owns the event
        ownerOrganizer = new User();
        ownerOrganizer.setId(1L);
        ownerOrganizer.setName("Event Owner");
        ownerOrganizer.setEmail("owner@test.com");
        Role organizerRole = new Role();
        organizerRole.setRoleName("ORGANIZER");
        ownerOrganizer.setRoles(Set.of(organizerRole));

        // Different organizer — should NOT be able to see owner's DRAFT
        differentOrganizer = new User();
        differentOrganizer.setId(2L);
        differentOrganizer.setName("Other Organizer");
        differentOrganizer.setEmail("other@test.com");
        differentOrganizer.setRoles(Set.of(organizerRole));

        // Admin — can see everything
        adminUser = new User();
        adminUser.setId(3L);
        adminUser.setName("Admin");
        adminUser.setEmail("admin@test.com");
        Role adminRole = new Role();
        adminRole.setRoleName("ADMIN");
        adminUser.setRoles(Set.of(adminRole));

        // DRAFT event — only owner and admin can see this
        draftEvent = new Event();
        draftEvent.setId(10L);
        draftEvent.setTitle("Private Draft Event");
        draftEvent.setStatus(EventStatus.DRAFT);
        draftEvent.setOrganizer(ownerOrganizer);
        draftEvent.setEventDate(LocalDate.now().plusDays(30));

        // PUBLISHED event — anyone can see this
        publishedEvent = new Event();
        publishedEvent.setId(11L);
        publishedEvent.setTitle("Public Event");
        publishedEvent.setStatus(EventStatus.PUBLISHED);
        publishedEvent.setOrganizer(ownerOrganizer);
        publishedEvent.setEventDate(LocalDate.now().plusDays(10));
    }

    /**
     * IDOR test 1 — a different organizer tries to access another
     * organizer's DRAFT event by guessing the event ID.
     *
     * Must return 404 (ResourceNotFoundException), not 403.
     * The non-disclosure principle: don't reveal the event exists.
     */
    @Test
    void getEventById_shouldThrow404_whenNonOwnerAccessesDraftEvent() {
        when(eventRepository.findById(10L))
                .thenReturn(Optional.of(draftEvent));

        // differentOrganizer tries to access ownerOrganizer's DRAFT
        ResourceNotFoundException ex = assertThrows(
                ResourceNotFoundException.class,
                () -> eventService.getEventById(10L, differentOrganizer)
        );

        // Must be 404 message, not 403
        assertTrue(ex.getMessage().contains("Event not found"));
    }

    /**
     * IDOR test 2 — anonymous user (null requester) tries to access
     * a DRAFT event. Must get 404.
     */
    @Test
    void getEventById_shouldThrow404_whenAnonymousUserAccessesDraftEvent() {
        when(eventRepository.findById(10L))
                .thenReturn(Optional.of(draftEvent));

        // null = anonymous user (not logged in)
        assertThrows(ResourceNotFoundException.class,
                () -> eventService.getEventById(10L, null));
    }

    /**
     * Owner can access their own DRAFT event — returns the event response.
     */
    @Test
    void getEventById_shouldReturnEvent_whenOwnerAccessesDraftEvent() {
        when(eventRepository.findById(10L))
                .thenReturn(Optional.of(draftEvent));

        // Owner accesses their own DRAFT — should succeed
        EventResponse response = eventService.getEventById(10L, ownerOrganizer);

        assertNotNull(response);
        assertEquals("Private Draft Event", response.getTitle());
    }

    /**
     * Admin can access any event regardless of ownership.
     */
    @Test
    void getEventById_shouldReturnEvent_whenAdminAccessesDraftEvent() {
        when(eventRepository.findById(10L))
                .thenReturn(Optional.of(draftEvent));

        // Admin accesses any DRAFT — should succeed
        EventResponse response = eventService.getEventById(10L, adminUser);

        assertNotNull(response);
        assertEquals("Private Draft Event", response.getTitle());
    }

    /**
     * PUBLISHED events are publicly visible — even anonymous users can see them.
     */
    @Test
    void getEventById_shouldReturnEvent_whenAnyoneAccessesPublishedEvent() {
        when(eventRepository.findById(11L))
                .thenReturn(Optional.of(publishedEvent));

        // Anonymous user accesses PUBLISHED event — should succeed
        EventResponse response = eventService.getEventById(11L, null);

        assertNotNull(response);
        assertEquals("Public Event", response.getTitle());
    }

    /**
     * IDOR test on getOwnedEvent (used by submitForApproval, cancelEvent).
     * A different organizer tries to cancel another organizer's event.
     * Must return 404.
     */
    @Test
    void cancelEvent_shouldThrow404_whenNonOwnerTriesToCancel() {
        // Make event DRAFT so it passes the status check
        draftEvent.setStatus(EventStatus.DRAFT);

        when(eventRepository.findById(10L))
                .thenReturn(Optional.of(draftEvent));

        // differentOrganizer tries to cancel ownerOrganizer's event
        assertThrows(ResourceNotFoundException.class,
                () -> eventService.cancelEvent(10L, differentOrganizer));
    }
}