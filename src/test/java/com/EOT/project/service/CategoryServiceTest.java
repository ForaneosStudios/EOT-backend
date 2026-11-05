package com.EOT.project.service;

import com.EOT.project.dto.request.CategoryRequestDTO;
import com.EOT.project.dto.response.CategoryResponseDTO;
import com.EOT.project.model.CategoryModel;
import com.EOT.project.repository.CategoryRepository;
import com.EOT.project.repository.ThreadRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ThreadRepository threadRepository;

    @InjectMocks
    private CategoryService categoryService;

    private CategoryModel category;

    @BeforeEach
    void setUp() {
        category = CategoryModel.builder()
                .categoryId(UUID.randomUUID())
                .categoryName("Estrategia")
                .description("Discusion sobre juegos de estrategia")
                .build();
    }

    @Test
    @DisplayName("Prueba unitaria para listar todas las categorias activas (findAll)")
    void testFindAllCategories() {
        CategoryModel secondCategory = CategoryModel.builder()
                .categoryId(UUID.randomUUID())
                .categoryName("Aventura")
                .description("Discusion sobre juegos de aventura")
                .build();
        given(categoryRepository.findByIsDeletedFalse())
                .willReturn(List.of(category, secondCategory));

        List<CategoryResponseDTO> result = categoryService.findAll();

        assertThat(result)
                .isNotNull()
                .hasSize(2)
                .extracting(CategoryResponseDTO::getCategoryName)
                .containsExactly("Estrategia", "Aventura");
        verify(categoryRepository, times(1)).findByIsDeletedFalse();
    }

    @Test
    @DisplayName("Prueba unitaria para crear y guardar una categoria (create)")
    void testCreateCategory() {
        CategoryRequestDTO request = new CategoryRequestDTO(
                "Estrategia",
                "Discusion sobre juegos de estrategia"
        );
        given(categoryRepository.existsByCategoryName(request.getCategoryName()))
                .willReturn(false);
        given(categoryRepository.save(any(CategoryModel.class))).willReturn(category);

        CategoryResponseDTO result = categoryService.create(request);

        assertThat(result).isNotNull();
        assertThat(result.getCategoryId()).isEqualTo(category.getCategoryId());
        assertThat(result.getCategoryName()).isEqualTo("Estrategia");
        assertThat(result.getDescription())
                .isEqualTo("Discusion sobre juegos de estrategia");

        ArgumentCaptor<CategoryModel> categoryCaptor =
                ArgumentCaptor.forClass(CategoryModel.class);
        verify(categoryRepository, times(1)).save(categoryCaptor.capture());
        assertThat(categoryCaptor.getValue().getCategoryName()).isEqualTo("Estrategia");
        assertThat(categoryCaptor.getValue().getDescription())
                .isEqualTo("Discusion sobre juegos de estrategia");
    }
}
