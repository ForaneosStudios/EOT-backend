package com.EOT.project.dto.request;

import jakarta.validation.constraints.NotNull;

public record ThreadLockRequestDTO(
        @NotNull(message = "El estado de bloqueo es obligatorio") Boolean locked) {
}
