package com.eventhive.eventhive_backend.repository;

import com.eventhive.eventhive_backend.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    // Derived query — same pattern as findByEmail / findByRoleName.
    Optional<Category> findByName(String name);
}