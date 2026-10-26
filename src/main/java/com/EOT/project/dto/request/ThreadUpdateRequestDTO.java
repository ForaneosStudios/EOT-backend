package com.EOT.project.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ThreadUpdateRequestDTO(
        @NotBlank(message = "El titulo del hilo es obligatorio")
        @Size(min = 3, max = 200, message = "El titulo debe tener entre 3 y 200 caracteres")
        String title,
        @NotBlank(message = "El contenido no puede estar vacio")
        @Size(max = 500, message = "El contenido no puede superar los 500 caracteres")
        String content) {
}
