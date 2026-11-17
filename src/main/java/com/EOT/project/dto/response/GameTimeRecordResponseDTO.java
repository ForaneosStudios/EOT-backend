package com.EOT.project.dto.response;

import com.EOT.project.model.UserModel;
import java.time.Instant;
import java.util.UUID;

public record GameTimeRecordResponseDTO(UUID userId, Long timeMs, Instant updatedAt) {
    public static GameTimeRecordResponseDTO fromModel(UserModel user) {
        return new GameTimeRecordResponseDTO(user.getUserId(), user.getBestTimeMs(), user.getUpdatedAt());
    }
}
