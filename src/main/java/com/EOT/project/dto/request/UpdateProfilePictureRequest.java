package com.EOT.project.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateProfilePictureRequest(
        @NotBlank
        @Pattern(regexp = "/avatars/avatar-[1-6]\\.svg", message = "Selecciona un avatar del catálogo")
        String profilePicture) {
}
