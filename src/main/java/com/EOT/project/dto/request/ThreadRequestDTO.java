package com.EOT.project.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThreadRequestDTO {
    private UUID categoryId;

    @NotNull(message = "El ID del usuario es obligatorio")
    private UUID userId;

    @NotBlank(message = "El título del hilo es obligatorio")
    @Size(min = 5, max = 120, message = "El título debe tener entre 5 y 120 caracteres")
    private String title;

    @NotBlank(message = "El contenido no puede estar vacío")
    @Size(min = 10, max = 2000, message = "El contenido debe tener entre 10 y 2000 caracteres")
    private String content;

    private Boolean isLocked;
}
