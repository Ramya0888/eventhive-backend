package com.eventhive.eventhive_backend.entity;

import com.eventhive.eventhive_backend.enums.BookingStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "bookings", indexes = {
        @Index(name = "idx_bookings_reference",      columnList = "booking_reference"),
        @Index(name = "idx_bookings_status_expires", columnList = "status, expires_at")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class Booking extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_reference", nullable = false, unique = true, length = 20)
    private String bookingReference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private BookingStatus status = BookingStatus.PENDING;

    @Column(name = "reserved_at")
    private LocalDateTime reservedAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    // When the PENDING reservation expires and seats auto-release.
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @OneToMany(mappedBy = "booking", fetch = FetchType.LAZY,
               cascade = CascadeType.ALL)
    @Builder.Default
    private List<BookingItem> items = new ArrayList<>();

    @Column(name = "checked_in")
@Builder.Default
private Boolean checkedIn = false;

@Column(name = "checked_in_at")
private LocalDateTime checkedInAt;
}