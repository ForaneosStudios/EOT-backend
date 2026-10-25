package com.EOT.project.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateProfileRequest(
        @NotBlank @Size(min = 3, max = 50) String username,
        @Size(max = 255) String profilePicture) {
}
