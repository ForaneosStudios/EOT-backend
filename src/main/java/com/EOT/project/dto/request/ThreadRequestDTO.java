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

    @NotNull(message = "El ID de la categoría es obligatorio")
    private UUID categoryId;

    @NotNull(message = "El ID del usuario es obligatorio")
    private UUID userId;

    @NotBlank(message = "El título del hilo es obligatorio")
    @Size(min = 3, max = 200, message = "El título debe tener entre 3 y 200 caracteres")
    private String title;

    @NotBlank(message = "El contenido no puede estar vacío")
    @Size(min = 1, max = 500, message = "El contenido debe tener entre 3 y 500 caracteres")
    private String content;

    private Boolean isLocked;
}

