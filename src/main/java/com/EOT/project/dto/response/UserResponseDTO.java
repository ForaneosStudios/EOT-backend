package com.EOT.project.dto.response;

import com.EOT.project.model.UserModel;
import com.EOT.project.model.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponseDTO {

    private UUID userId;
    private String username;
    private String email;
    private UserRole role;
    private String profilePicture;
    private Instant createdAt;
    private Instant updatedAt;

    public static UserResponseDTO fromModel(UserModel user) {
        if (user == null) {
            return null;
        }
        return UserResponseDTO.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole())
                .profilePicture(user.getProfilePicture())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}

