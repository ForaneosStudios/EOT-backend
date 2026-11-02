package com.EOT.project.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "post_reports")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PostReportModel extends AuditableModel {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "report_id", updatable = false, nullable = false)
    private UUID reportId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "post_id", nullable = false)
    private PostModel post;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "reporter_id", nullable = false)
    private UserModel reporter;
    @Column(nullable = false, length = 500) private String reason;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 12) @Builder.Default
    private PostReportStatus status = PostReportStatus.PENDING;
    @Column(name = "reviewed_at") private Instant reviewedAt;
}
