package com.eventhive.eventhive_backend.dto;

import com.eventhive.eventhive_backend.entity.Booking;
import com.eventhive.eventhive_backend.entity.BookingItem;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Builder
public class BookingResponse {

    private Long id;
    private String bookingReference;
    private String eventTitle;
    private String status;
    private BigDecimal totalAmount;
    private LocalDateTime reservedAt;
    private LocalDateTime expiresAt;
    private LocalDateTime confirmedAt;
    private List<BookingItemDto> items;
    private Long eventId;  

    @Getter
    @Builder
    public static class BookingItemDto {
        private Long seatId;
        private String seatNumber;
        private String categoryName;
        private BigDecimal priceAtBooking;
    }

    public static BookingResponse from(Booking booking) {
        List<BookingItemDto> itemDtos = booking.getItems().stream()
                .map(item -> BookingItemDto.builder()
                        .seatId(item.getSeat().getId())
                        .seatNumber(item.getSeat().getSeatNumber())
                        .categoryName(item.getSeatCategory().getName())
                        .priceAtBooking(item.getPriceAtBooking())
                        .build())
                .collect(Collectors.toList());

        return BookingResponse.builder()
                .id(booking.getId())
                .bookingReference(booking.getBookingReference())
                .eventTitle(booking.getEvent().getTitle())
                .status(booking.getStatus().name())
                .totalAmount(booking.getTotalAmount())
                .reservedAt(booking.getReservedAt())
                .expiresAt(booking.getExpiresAt())
                .confirmedAt(booking.getConfirmedAt())
                .items(itemDtos)
                .eventId(booking.getEvent().getId())
                .build();
    }
}