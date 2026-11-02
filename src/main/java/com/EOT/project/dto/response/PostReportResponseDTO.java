package com.EOT.project.dto.response;

import com.EOT.project.model.PostReportModel;
import com.EOT.project.model.PostReportStatus;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PostReportResponseDTO {
    private UUID reportId; private UUID postId; private UUID reporterId; private String reporterUsername;
    private String reason; private PostReportStatus status; private Instant createdAt; private Instant reviewedAt;
    public static PostReportResponseDTO fromModel(PostReportModel report) {
        return PostReportResponseDTO.builder().reportId(report.getReportId()).postId(report.getPost().getPostId())
                .reporterId(report.getReporter().getUserId()).reporterUsername(report.getReporter().getUsername())
                .reason(report.getReason()).status(report.getStatus()).createdAt(report.getCreatedAt()).reviewedAt(report.getReviewedAt()).build();
    }
}
