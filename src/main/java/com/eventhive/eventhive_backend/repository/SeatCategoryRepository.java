package com.eventhive.eventhive_backend.repository;

import com.eventhive.eventhive_backend.entity.SeatCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SeatCategoryRepository extends JpaRepository<SeatCategory, Long> {
    List<SeatCategory> findByEventId(Long eventId);
}