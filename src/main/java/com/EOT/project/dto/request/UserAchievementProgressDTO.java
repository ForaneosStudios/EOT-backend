package com.EOT.project.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAchievementProgressDTO {

    @NotNull(message = "El ID del usuario es obligatorio")
    private UUID userId;

    @NotNull(message = "El ID del logro es obligatorio")
    private UUID achId;

    @NotNull(message = "El progreso no puede ser nulo")
    @Min(value = 0, message = "El progreso no puede ser menor a 0")
    private Integer progress;

    private Boolean isUnlocked;
}

