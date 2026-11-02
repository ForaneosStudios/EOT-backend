package com.EOT.project.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AchievementRequestDTO {

    @NotBlank(message = "El nombre del logro es obligatorio")
    @Size(min = 2, max = 100, message = "El nombre del logro debe tener entre 2 y 100 caracteres")
    private String achName;

    @NotBlank(message = "La descripción del logro es obligatoria")
    @Size(max = 500, message = "La descripción no puede superar los 500 caracteres")
    private String achDescription;

    private String achPicture;

    private Boolean isAchHidden;

    @Min(value = 1, message = "La meta del contador debe ser mayor a cero")
    private Integer targetValue;

    @Size(max = 50, message = "La etiqueta de progreso no puede superar los 50 caracteres")
    private String progressLabel;
}

