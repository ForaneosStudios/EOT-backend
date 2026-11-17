package com.EOT.project.dto.response;

import com.EOT.project.model.PrivateMessageModel;
import java.time.Instant;
import java.util.UUID;

public record PrivateMessageResponseDTO(UUID messageId, UUID conversationId, UUID senderId,
                                        String senderUsername, String content, Instant createdAt, Instant readAt) {
    public static PrivateMessageResponseDTO fromModel(PrivateMessageModel message) {
        return new PrivateMessageResponseDTO(message.getMessageId(), message.getConversation().getConversationId(),
                message.getSender().getUserId(), message.getSender().getUsername(), message.getContent(),
                message.getCreatedAt(), message.getReadAt());
    }
}
