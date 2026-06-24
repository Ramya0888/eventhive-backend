package com.eventhive.eventhive_backend.entity;

import com.eventhive.eventhive_backend.enums.SeatStatus;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "seats", indexes = {
        @Index(name = "idx_seats_event_status", columnList = "event_id, status")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class Seat extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seat_category_id", nullable = false)
    private SeatCategory seatCategory;

    @Column(name = "seat_number", nullable = false, length = 20)
    private String seatNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private SeatStatus status = SeatStatus.AVAILABLE;
}