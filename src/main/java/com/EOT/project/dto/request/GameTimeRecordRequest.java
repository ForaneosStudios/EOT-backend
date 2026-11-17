package com.EOT.project.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record GameTimeRecordRequest(
        @NotNull @Positive(message = "El tiempo debe ser mayor que cero") Long timeMs) {
}
