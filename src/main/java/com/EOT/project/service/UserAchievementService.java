package com.EOT.project.service;

import com.EOT.project.dto.request.UserAchievementProgressDTO;
import com.EOT.project.dto.response.UserAchievementResponseDTO;
import com.EOT.project.exception.ResourceNotFoundException;
import com.EOT.project.model.AchievementModel;
import com.EOT.project.model.UserAchievementModel;
import com.EOT.project.model.UserAchievementModelId;
import com.EOT.project.model.UserModel;
import com.EOT.project.repository.AchievementRepository;
import com.EOT.project.repository.UserAchievementRepository;
import com.EOT.project.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserAchievementService {

    private final UserAchievementRepository userAchievementRepository;
    private final UserRepository userRepository;
    private final AchievementRepository achievementRepository;

    @Transactional(readOnly = true)
    public List<UserAchievementResponseDTO> findByUserId(UUID userId) {
        return userAchievementRepository.findById_UserId(userId)
                .stream()
                .map(UserAchievementResponseDTO::fromModel)
                .toList();
    }

    @Transactional
    public UserAchievementResponseDTO updateProgress(UserAchievementProgressDTO dto) {
        UserAchievementModelId id = new UserAchievementModelId(dto.getUserId(), dto.getAchId());

        UserAchievementModel record = userAchievementRepository.findById(id).orElseGet(() -> {
            UserModel user = userRepository.findById(dto.getUserId())
                    .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con ID: " + dto.getUserId()));
            AchievementModel achievement = achievementRepository.findById(dto.getAchId())
                    .orElseThrow(() -> new ResourceNotFoundException("Logro no encontrado con ID: " + dto.getAchId()));

            return UserAchievementModel.builder()
                    .id(id)
                    .user(user)
                    .achievement(achievement)
                    .progress(0)
                    .isUnlocked(false)
                    .build();
        });

        record.setProgress(dto.getProgress());
        if (Boolean.TRUE.equals(dto.getIsUnlocked()) || dto.getProgress() >= 100) {
            record.setIsUnlocked(true);
            if (record.getUnlockedAt() == null) {
                record.setUnlockedAt(Instant.now());
            }
        }

        return UserAchievementResponseDTO.fromModel(userAchievementRepository.save(record));
    }
}

