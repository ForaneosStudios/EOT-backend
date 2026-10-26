package com.EOT.project.dto.response;

import com.EOT.project.model.UserAchievementModel;
import com.EOT.project.model.AchievementModel;
import com.EOT.project.model.UserModel;
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
    private Boolean isAchHidden;
    private Integer progress;
    private Boolean isUnlocked;
    private Instant unlockedAt;
    private Instant updatedAt;

    public static UserAchievementResponseDTO fromModel(UserAchievementModel ua) {
        if (ua == null) {
            return null;
        }
        boolean hiddenAndLocked = ua.getAchievement() != null
                && Boolean.TRUE.equals(ua.getAchievement().getIsAchHidden())
                && !Boolean.TRUE.equals(ua.getIsUnlocked());
        return UserAchievementResponseDTO.builder()
                .userId(ua.getUser() != null ? ua.getUser().getUserId() : null)
                .username(ua.getUser() != null ? ua.getUser().getUsername() : null)
                .achId(ua.getAchievement() != null ? ua.getAchievement().getAchId() : null)
                .achName(hiddenAndLocked ? "Logro oculto"
                        : ua.getAchievement() != null ? ua.getAchievement().getAchName() : null)
                .achDescription(hiddenAndLocked ? null
                        : ua.getAchievement() != null ? ua.getAchievement().getAchDescription() : null)
                .achPicture(hiddenAndLocked ? null
                        : ua.getAchievement() != null ? ua.getAchievement().getAchPicture() : null)
                .isAchHidden(ua.getAchievement() != null ? ua.getAchievement().getIsAchHidden() : null)
                .progress(ua.getProgress())
                .isUnlocked(ua.getIsUnlocked())
                .unlockedAt(ua.getUnlockedAt())
                .updatedAt(ua.getUpdatedAt())
                .build();
    }

    public static UserAchievementResponseDTO withoutProgress(UserModel user, AchievementModel achievement) {
        boolean hidden = Boolean.TRUE.equals(achievement.getIsAchHidden());
        return UserAchievementResponseDTO.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .achId(achievement.getAchId())
                .achName(hidden ? "Logro oculto" : achievement.getAchName())
                .achDescription(hidden ? null : achievement.getAchDescription())
                .achPicture(hidden ? null : achievement.getAchPicture())
                .isAchHidden(achievement.getIsAchHidden())
                .progress(0)
                .isUnlocked(false)
                .build();
    }
}

