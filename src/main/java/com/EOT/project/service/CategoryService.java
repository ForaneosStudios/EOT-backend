package com.EOT.project.service;

import com.EOT.project.dto.request.CategoryRequestDTO;
import com.EOT.project.dto.response.CategoryResponseDTO;
import com.EOT.project.exception.ConflictException;
import com.EOT.project.exception.ResourceNotFoundException;
import com.EOT.project.model.CategoryModel;
import com.EOT.project.repository.CategoryRepository;
import com.EOT.project.repository.ThreadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ThreadRepository threadRepository;

    @Transactional(readOnly = true)
    public List<CategoryResponseDTO> findAll() {
        return categoryRepository.findByIsDeletedFalse()
                .stream()
                .map(CategoryResponseDTO::fromModel)
                .toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponseDTO findById(UUID id) {
        return CategoryResponseDTO.fromModel(requireActive(id));
    }

    @Transactional
    public CategoryResponseDTO create(CategoryRequestDTO dto) {
        if (categoryRepository.existsByCategoryName(dto.getCategoryName())) {
            throw new ConflictException("La categoría '" + dto.getCategoryName() + "' ya existe");
        }

        CategoryModel category = CategoryModel.builder()
                .categoryName(dto.getCategoryName())
                .description(dto.getDescription())
                .build();

        return CategoryResponseDTO.fromModel(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponseDTO update(UUID id, CategoryRequestDTO dto) {
        CategoryModel category = requireActive(id);
        categoryRepository.findByCategoryName(dto.getCategoryName())
                .filter(existing -> !existing.getCategoryId().equals(id))
                .ifPresent(existing -> {
                    throw new ConflictException("La categoría '" + dto.getCategoryName() + "' ya existe");
                });
        category.setCategoryName(dto.getCategoryName());
        category.setDescription(dto.getDescription());
        return CategoryResponseDTO.fromModel(categoryRepository.save(category));
    }

    @Transactional
    public void delete(UUID id) {
        CategoryModel category = requireActive(id);
        if (threadRepository.existsByCategory_CategoryIdAndIsDeletedFalse(id)) {
            throw new ConflictException("No se puede eliminar una categoría con hilos activos");
        }
        category.setIsDeleted(true);
        category.setDeletedAt(Instant.now());
        categoryRepository.save(category);
    }

    private CategoryModel requireActive(UUID id) {
        return categoryRepository.findById(id)
                .filter(category -> !Boolean.TRUE.equals(category.getIsDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Categoría no encontrada con ID: " + id));
    }
}

