package com.EOT.project.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PostUpdateRequestDTO(
        @NotBlank(message = "El contenido del post no puede estar vacio")
        @Size(max = 500, message = "El contenido no puede superar los 500 caracteres")
        String content) {
}
