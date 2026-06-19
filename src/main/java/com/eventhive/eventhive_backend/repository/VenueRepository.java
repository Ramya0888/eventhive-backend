package com.eventhive.eventhive_backend.repository;

import com.eventhive.eventhive_backend.entity.Venue;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VenueRepository extends JpaRepository<Venue, Long> {
}