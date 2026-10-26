package com.EOT.project.dto.response;

import com.EOT.project.model.AchievementModel;
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
public class AchievementResponseDTO {

    private UUID achId;
    private String achName;
    private String achDescription;
    private String achPicture;
    private Boolean isAchHidden;
    private Instant createdAt;

    public static AchievementResponseDTO fromModel(AchievementModel achievement) {
        if (achievement == null) {
            return null;
        }
        return AchievementResponseDTO.builder()
                .achId(achievement.getAchId())
                .achName(achievement.getAchName())
                .achDescription(achievement.getAchDescription())
                .achPicture(achievement.getAchPicture())
                .isAchHidden(achievement.getIsAchHidden())
                .createdAt(achievement.getCreatedAt())
                .build();
    }
}

