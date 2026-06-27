package com.eventhive.eventhive_backend.entity;

import com.eventhive.eventhive_backend.enums.EventStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;

import java.util.List;

@Entity
@Table(name = "events", indexes = {
        // Mirror the composite index in the entity so Hibernate creates it too.
        @Index(name = "idx_events_status_date", columnList = "status, event_date")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class Event extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    // @Enumerated(STRING): store the NAME ("DRAFT"), not the ordinal position (0).
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private EventStatus status = EventStatus.DRAFT;

    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    @Column(name = "banner_url", length = 500)
    private String bannerUrl;

    @Column(name = "available_seats")
    private Integer availableSeats;

    @Column(name = "average_rating")
    private Double averageRating;

    // ---------- Relationships (all LAZY on purpose) ----------

    // Many events -> ONE organizer. The events table owns the FK (organizer_id).
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organizer_id", nullable = false)
    private User organizer;

    // Many events -> ONE category.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    // Many events -> ONE venue.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venue_id")
    private Venue venue;

    // NOTE: @OneToMany List<Seat> is added in Module 3 (Seat Management).
    // One event has many seats. CascadeType.ALL: deleting an event
    // cascades to delete its seats. mappedBy points to the owning side.
    @OneToMany(mappedBy = "event", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @Builder.Default
    private List<SeatCategory> seatCategories = new ArrayList<>();
}