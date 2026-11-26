package com.EOT.project.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** The photo as a base64 data URL (the app already reduced it) plus the player's explicit consent. */
public record UpdateProfilePhotoRequest(
        @NotBlank(message = "La foto es obligatoria")
        @Size(max = 800_000, message = "La foto es demasiado grande")
        String image,
        @NotNull(message = "Debes aceptar que tu foto sea visible para otros jugadores")
        @AssertTrue(message = "Debes aceptar que tu foto sea visible para otros jugadores")
        Boolean consent) {
}
