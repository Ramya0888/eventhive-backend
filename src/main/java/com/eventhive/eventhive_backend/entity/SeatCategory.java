package com.eventhive.eventhive_backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "seat_categories")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class SeatCategory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Many seat categories belong to one event.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(nullable = false, length = 50)
    private String name;

    
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "total_count", nullable = false)
    private Integer totalCount;

    // Convenience: the seats generated for this category.
    @OneToMany(mappedBy = "seatCategory", fetch = FetchType.LAZY,
               cascade = CascadeType.ALL)
    @Builder.Default
    private List<Seat> seats = new ArrayList<>();
}