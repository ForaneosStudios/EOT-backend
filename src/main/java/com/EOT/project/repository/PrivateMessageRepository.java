package com.EOT.project.repository;

import com.EOT.project.model.PrivateMessageModel;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PrivateMessageRepository extends JpaRepository<PrivateMessageModel, UUID> {
    Page<PrivateMessageModel> findByConversation_ConversationIdAndIsDeletedFalseOrderByCreatedAtAsc(UUID conversationId, Pageable pageable);
    Optional<PrivateMessageModel> findTopByConversation_ConversationIdAndIsDeletedFalseOrderByCreatedAtDesc(UUID conversationId);
    long countByConversation_ConversationIdAndSender_UserIdNotAndReadAtIsNullAndIsDeletedFalse(UUID conversationId, UUID userId);

    @Modifying
    @Query("update PrivateMessageModel m set m.readAt = :readAt where m.conversation.conversationId = :conversationId "
            + "and m.sender.userId <> :readerId and m.readAt is null and m.isDeleted = false")
    int markUnreadIncomingAsRead(@Param("conversationId") UUID conversationId, @Param("readerId") UUID readerId,
                                 @Param("readAt") Instant readAt);
}
