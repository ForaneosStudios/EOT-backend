package com.EOT.project.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateProfileRequest(
        @NotBlank @Size(min = 4, max = 50) @jakarta.validation.constraints.Pattern(regexp = "[a-zA-Z0-9_]+") String username,
        @Size(max = 255) String profilePicture) {
}
