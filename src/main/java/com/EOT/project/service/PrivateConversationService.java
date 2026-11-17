package com.EOT.project.service;

import com.EOT.project.dto.response.PrivateConversationResponseDTO;
import com.EOT.project.dto.response.PrivateMessageResponseDTO;
import com.EOT.project.exception.BadRequestException;
import com.EOT.project.exception.ResourceNotFoundException;
import com.EOT.project.model.PrivateConversationModel;
import com.EOT.project.model.PrivateMessageModel;
import com.EOT.project.model.UserModel;
import com.EOT.project.repository.PrivateConversationRepository;
import com.EOT.project.repository.PrivateMessageRepository;
import com.EOT.project.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class PrivateConversationService {
    private final PrivateConversationRepository conversations;
    private final PrivateMessageRepository messages;
    private final UserRepository users;

    @Transactional(readOnly = true)
    public List<PrivateConversationResponseDTO> findAllFor(UserModel current) {
        return conversations.findAllForParticipant(current.getUserId()).stream()
                .map(conversation -> toResponse(conversation, current.getUserId())).toList();
    }

    @Transactional(readOnly = true)
    public PrivateConversationResponseDTO findById(UUID conversationId, UserModel current) {
        return toResponse(requireParticipant(conversationId, current.getUserId()), current.getUserId());
    }

    @Transactional
    public PrivateConversationResponseDTO createOrFind(UUID recipientId, UserModel current) {
        if (current.getUserId().equals(recipientId)) throw new BadRequestException("No puedes crear un chat contigo mismo");
        UserModel recipient = users.findById(recipientId).filter(user -> !Boolean.TRUE.equals(user.getIsDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Jugador no encontrado con ID: " + recipientId));
        PrivateConversationModel conversation = conversations.findBetween(current.getUserId(), recipientId)
                .orElseGet(() -> {
                    boolean currentIsFirst = current.getUserId().compareTo(recipientId) < 0;
                    return conversations.save(PrivateConversationModel.builder()
                            .participantOne(currentIsFirst ? current : recipient)
                            .participantTwo(currentIsFirst ? recipient : current).build());
                });
        return toResponse(conversation, current.getUserId());
    }

    @Transactional(readOnly = true)
    public Page<PrivateMessageResponseDTO> findMessages(UUID conversationId, UserModel current, Pageable pageable) {
        requireParticipant(conversationId, current.getUserId());
        return messages.findByConversation_ConversationIdAndIsDeletedFalseOrderByCreatedAtAsc(conversationId, pageable)
                .map(PrivateMessageResponseDTO::fromModel);
    }

    @Transactional
    public PrivateMessageResponseDTO sendMessage(UUID conversationId, String content, UserModel current) {
        PrivateConversationModel conversation = requireParticipant(conversationId, current.getUserId());
        PrivateMessageModel message = messages.save(PrivateMessageModel.builder().conversation(conversation)
                .sender(current).content(content.trim()).build());
        conversation.setUpdatedAt(Instant.now());
        conversations.save(conversation);
        return PrivateMessageResponseDTO.fromModel(message);
    }

    @Transactional
    public void markAsRead(UUID conversationId, UserModel current) {
        requireParticipant(conversationId, current.getUserId());
        messages.markUnreadIncomingAsRead(conversationId, current.getUserId(), Instant.now());
    }

    private PrivateConversationResponseDTO toResponse(PrivateConversationModel conversation, UUID currentUserId) {
        PrivateMessageModel last = messages.findTopByConversation_ConversationIdAndIsDeletedFalseOrderByCreatedAtDesc(conversation.getConversationId()).orElse(null);
        long unread = messages.countByConversation_ConversationIdAndSender_UserIdNotAndReadAtIsNullAndIsDeletedFalse(conversation.getConversationId(), currentUserId);
        return PrivateConversationResponseDTO.fromModel(conversation, currentUserId, last, unread);
    }

    private PrivateConversationModel requireParticipant(UUID conversationId, UUID userId) {
        PrivateConversationModel conversation = conversations.findById(conversationId)
                .filter(item -> !Boolean.TRUE.equals(item.getIsDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Conversación no encontrada con ID: " + conversationId));
        if (!conversation.getParticipantOne().getUserId().equals(userId)
                && !conversation.getParticipantTwo().getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No perteneces a esta conversación");
        }
        return conversation;
    }
}
