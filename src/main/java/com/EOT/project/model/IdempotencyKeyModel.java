package com.EOT.project.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

/**
 * Stored outcome of a mutation sent with an {@code Idempotency-Key} header.
 * Keys are scoped to the authenticated subject, so two users can reuse the same key.
 */
@Entity
@Table(name = "idempotency_keys",
        uniqueConstraints = @UniqueConstraint(name = "uq_idempotency_keys_owner_key",
                columnNames = {"auth_sub", "idem_key"}),
        indexes = @Index(name = "idx_idempotency_keys_expires_at", columnList = "expires_at"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class IdempotencyKeyModel {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "idempotency_id", updatable = false, nullable = false)
    private UUID id;
    @Column(name = "auth_sub", nullable = false, length = 64) private String authSub;
    @Column(name = "idem_key", nullable = false, length = 128) private String idemKey;
    @Column(name = "request_method", nullable = false, length = 10) private String requestMethod;
    @Column(name = "request_path", nullable = false, length = 512) private String requestPath;
    @Column(name = "request_hash", nullable = false, length = 64) private String requestHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 12) private IdempotencyStatus status;
    @Column(name = "response_status") private Integer responseStatus;
    @Column(name = "response_body", columnDefinition = "TEXT") private String responseBody;
    @Column(name = "response_content_type", length = 255) private String responseContentType;
    @Column(name = "location_header", length = 1024) private String locationHeader;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "locked_until", nullable = false) private Instant lockedUntil;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
}
