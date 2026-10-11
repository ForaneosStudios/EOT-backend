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
public class PostRequestDTO {

    @NotNull(message = "El ID del hilo es obligatorio")
    private UUID threadId;

    @NotNull(message = "El ID del usuario es obligatorio")
    private UUID userId;

    private UUID parentPostId; // Opcional, para responder a otro post en el hilo

    @NotBlank(message = "El contenido del post no puede estar vacío")
    @Size(min = 1, max = 500, message = "El contenido debe tener entre 1 y 500 caracteres")
    private String content;
}

