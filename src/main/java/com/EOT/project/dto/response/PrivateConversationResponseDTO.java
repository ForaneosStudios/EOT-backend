package com.EOT.project.dto.response;

import com.EOT.project.model.PrivateConversationModel;
import com.EOT.project.model.PrivateMessageModel;
import com.EOT.project.model.UserModel;
import java.time.Instant;
import java.util.UUID;

public record PrivateConversationResponseDTO(UUID conversationId, UUID participantId, String participantUsername,
        String participantProfilePicture, String lastMessage, Instant lastMessageAt, long unreadCount, Instant createdAt) {
    public static PrivateConversationResponseDTO fromModel(PrivateConversationModel conversation, UUID currentUserId,
                                                            PrivateMessageModel lastMessage, long unreadCount) {
        UserModel counterpart = conversation.getParticipantOne().getUserId().equals(currentUserId)
                ? conversation.getParticipantTwo() : conversation.getParticipantOne();
        return new PrivateConversationResponseDTO(conversation.getConversationId(), counterpart.getUserId(),
                counterpart.getUsername(), counterpart.getProfilePicture(),
                lastMessage == null ? null : lastMessage.getContent(),
                lastMessage == null ? null : lastMessage.getCreatedAt(), unreadCount, conversation.getCreatedAt());
    }
}
