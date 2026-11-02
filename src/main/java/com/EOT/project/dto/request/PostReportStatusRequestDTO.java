package com.EOT.project.dto.request;

import com.EOT.project.model.PostReportStatus;
import jakarta.validation.constraints.NotNull;
public record PostReportStatusRequestDTO(@NotNull PostReportStatus status) {}
