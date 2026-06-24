package com.eventhive.eventhive_backend.repository;

import com.eventhive.eventhive_backend.entity.BookingItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BookingItemRepository extends JpaRepository<BookingItem, Long> {
    List<BookingItem> findByBookingId(Long bookingId);
}