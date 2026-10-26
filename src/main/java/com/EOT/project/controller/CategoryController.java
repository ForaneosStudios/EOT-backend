package com.EOT.project.controller;

import com.EOT.project.dto.request.CategoryRequestDTO;
import com.EOT.project.dto.response.CategoryResponseDTO;
import com.EOT.project.service.CategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    public ResponseEntity<List<CategoryResponseDTO>> getAllCategories() {
        return ResponseEntity.ok(categoryService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<CategoryResponseDTO> getCategoryById(@PathVariable UUID id) {
        return ResponseEntity.ok(categoryService.findById(id));
    }

    @PostMapping
    public ResponseEntity<CategoryResponseDTO> createCategory(@Valid @RequestBody CategoryRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryService.create(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CategoryResponseDTO> updateCategory(@PathVariable UUID id,
                                                               @Valid @RequestBody CategoryRequestDTO dto) {
        return ResponseEntity.ok(categoryService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable UUID id) {
        categoryService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

