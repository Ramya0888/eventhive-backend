package com.eventhive.eventhive_backend.enums;

/**
 * EventStatus — the lifecycle of an event.
 * DRAFT → PUBLISHED → ONGOING → COMPLETED, or → CANCELLED at any point.
 */
public enum EventStatus {
    DRAFT,              // organizer is still editing; not visible publicly
    PENDING_APPROVAL,   // submitted by organizer, awaiting admin review
    PUBLISHED,          // approved & live in the public catalog
    ONGOING,            // event is currently happening
    COMPLETED,          // event has finished
    CANCELLED           // called off
}