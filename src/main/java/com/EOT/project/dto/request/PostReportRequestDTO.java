package com.EOT.project.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record PostReportRequestDTO(@NotBlank @Size(max = 500) String reason) {}
