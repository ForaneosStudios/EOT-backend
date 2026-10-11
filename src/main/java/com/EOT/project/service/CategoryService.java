package com.EOT.project.service;

import com.EOT.project.dto.request.CategoryRequestDTO;
import com.EOT.project.dto.response.CategoryResponseDTO;
import com.EOT.project.exception.BadRequestException;
import com.EOT.project.exception.ResourceNotFoundException;
import com.EOT.project.model.CategoryModel;
import com.EOT.project.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public List<CategoryResponseDTO> findAll() {
        return categoryRepository.findByIsDeletedFalse()
                .stream()
                .map(CategoryResponseDTO::fromModel)
                .toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponseDTO findById(UUID id) {
        CategoryModel category = categoryRepository.findById(id)
                .filter(c -> !c.getIsDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Categoría no encontrada con ID: " + id));
        return CategoryResponseDTO.fromModel(category);
    }

    @Transactional
    public CategoryResponseDTO create(CategoryRequestDTO dto) {
        if (categoryRepository.existsByCategoryName(dto.getCategoryName())) {
            throw new BadRequestException("La categoría '" + dto.getCategoryName() + "' ya existe");
        }

        CategoryModel category = CategoryModel.builder()
                .categoryName(dto.getCategoryName())
                .description(dto.getDescription())
                .build();

        return CategoryResponseDTO.fromModel(categoryRepository.save(category));
    }
}

