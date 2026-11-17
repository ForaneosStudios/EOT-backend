package com.EOT.project.repository;

import com.EOT.project.model.PrivateConversationModel;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PrivateConversationRepository extends JpaRepository<PrivateConversationModel, UUID> {
    @Query("select c from PrivateConversationModel c where c.isDeleted = false and "
            + "(c.participantOne.userId = :userId or c.participantTwo.userId = :userId) order by c.updatedAt desc")
    List<PrivateConversationModel> findAllForParticipant(@Param("userId") UUID userId);

    @Query("select c from PrivateConversationModel c where c.isDeleted = false and "
            + "((c.participantOne.userId = :firstId and c.participantTwo.userId = :secondId) or "
            + "(c.participantOne.userId = :secondId and c.participantTwo.userId = :firstId))")
    Optional<PrivateConversationModel> findBetween(@Param("firstId") UUID firstId, @Param("secondId") UUID secondId);
}
