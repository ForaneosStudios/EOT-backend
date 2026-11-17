package com.EOT.project.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "private_messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PrivateMessageModel extends AuditableModel {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "message_id", updatable = false, nullable = false)
    private UUID messageId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private PrivateConversationModel conversation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false)
    private UserModel sender;

    @Column(name = "content", nullable = false, length = 2000)
    private String content;

    @Column(name = "read_at")
    private Instant readAt;
}
