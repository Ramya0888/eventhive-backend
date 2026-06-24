package com.eventhive.eventhive_backend.dto;

import com.eventhive.eventhive_backend.entity.Seat;
import com.eventhive.eventhive_backend.entity.SeatCategory;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Builder
public class SeatMapResponse {

    private Long eventId;
    private List<SeatCategoryDto> categories;

    @Getter
    @Builder
    public static class SeatCategoryDto {
        private Long id;
        private String name;
        private BigDecimal price;
        private List<SeatDto> seats;

        public static SeatCategoryDto from(SeatCategory cat, List<Seat> seats) {
            return SeatCategoryDto.builder()
                    .id(cat.getId())
                    .name(cat.getName())
                    .price(cat.getPrice())
                    .seats(seats.stream().map(SeatDto::from).collect(Collectors.toList()))
                    .build();
        }
    }

    @Getter
    @Builder
    public static class SeatDto {
        private Long id;
        private String seatNumber;
        private String status;

        public static SeatDto from(Seat seat) {
            return SeatDto.builder()
                    .id(seat.getId())
                    .seatNumber(seat.getSeatNumber())
                    .status(seat.getStatus().name())
                    .build();
        }
    }
}