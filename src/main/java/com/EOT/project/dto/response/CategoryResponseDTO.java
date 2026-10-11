package com.EOT.project.dto.response;

import com.EOT.project.model.CategoryModel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryResponseDTO {

    private UUID categoryId;
    private String categoryName;
    private String description;
    private Instant createdAt;

    public static CategoryResponseDTO fromModel(CategoryModel category) {
        if (category == null) {
            return null;
        }
        return CategoryResponseDTO.builder()
                .categoryId(category.getCategoryId())
                .categoryName(category.getCategoryName())
                .description(category.getDescription())
                .createdAt(category.getCreatedAt())
                .build();
    }
}

