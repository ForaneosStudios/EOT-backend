package com.EOT.project.dto.response;

import com.EOT.project.model.UserAchievementModel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAchievementResponseDTO {

    private UUID userId;
    private String username;
    private UUID achId;
    private String achName;
    private String achDescription;
    private String achPicture;
    private Integer progress;
    private Boolean isUnlocked;
    private Instant unlockedAt;
    private Instant updatedAt;

    public static UserAchievementResponseDTO fromModel(UserAchievementModel ua) {
        if (ua == null) {
            return null;
        }
        return UserAchievementResponseDTO.builder()
                .userId(ua.getUser() != null ? ua.getUser().getUserId() : null)
                .username(ua.getUser() != null ? ua.getUser().getUsername() : null)
                .achId(ua.getAchievement() != null ? ua.getAchievement().getAchId() : null)
                .achName(ua.getAchievement() != null ? ua.getAchievement().getAchName() : null)
                .achDescription(ua.getAchievement() != null ? ua.getAchievement().getAchDescription() : null)
                .achPicture(ua.getAchievement() != null ? ua.getAchievement().getAchPicture() : null)
                .progress(ua.getProgress())
                .isUnlocked(ua.getIsUnlocked())
                .unlockedAt(ua.getUnlockedAt())
                .updatedAt(ua.getUpdatedAt())
                .build();
    }
}

