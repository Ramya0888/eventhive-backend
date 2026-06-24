package com.eventhive.eventhive_backend.controller;

import com.eventhive.eventhive_backend.dto.ApiResponse;
import com.eventhive.eventhive_backend.entity.Category;
import com.eventhive.eventhive_backend.repository.CategoryRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryRepository categoryRepository;

    public CategoryController(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Category>>> getAllCategories() {
        return ResponseEntity.ok(
            ApiResponse.success("Categories fetched", categoryRepository.findAll()));
    }
}