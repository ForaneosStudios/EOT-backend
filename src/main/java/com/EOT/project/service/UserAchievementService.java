package com.EOT.project.service;

import com.EOT.project.dto.request.UserAchievementProgressDTO;
import com.EOT.project.dto.response.UserAchievementResponseDTO;
import com.EOT.project.exception.BadRequestException;
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
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserAchievementService {

    private final UserAchievementRepository userAchievementRepository;
    private final UserRepository userRepository;
    private final AchievementRepository achievementRepository;

    @Transactional(readOnly = true)
    public List<UserAchievementResponseDTO> findByUserId(UUID userId) {
        UserModel user = requireActiveUser(userId);
        Map<UUID, UserAchievementModel> progressByAchievement = userAchievementRepository
                .findById_UserIdAndIsDeletedFalse(userId).stream()
                .collect(Collectors.toMap(record -> record.getId().getAchId(), Function.identity()));

        return achievementRepository.findByIsDeletedFalse().stream()
                .map(achievement -> {
                    UserAchievementModel progress = progressByAchievement.get(achievement.getAchId());
                    return progress == null
                            ? UserAchievementResponseDTO.withoutProgress(user, achievement)
                            : UserAchievementResponseDTO.fromModel(progress);
                })
                .toList();
    }

    @Transactional
    public UserAchievementResponseDTO updateProgress(UserAchievementProgressDTO dto) {
        if (dto.getProgress() == null || dto.getProgress() < 0 || dto.getProgress() > 100) {
            throw new BadRequestException("El progreso debe estar entre 0 y 100");
        }
        UserAchievementModelId id = new UserAchievementModelId(dto.getUserId(), dto.getAchId());
        UserModel user = requireActiveUser(dto.getUserId());
        AchievementModel achievement = achievementRepository.findById(dto.getAchId())
                .filter(existing -> !Boolean.TRUE.equals(existing.getIsDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Logro no encontrado con ID: " + dto.getAchId()));

        UserAchievementModel record = userAchievementRepository.findById(id).orElseGet(() -> {
            return UserAchievementModel.builder()
                    .id(id)
                    .user(user)
                    .achievement(achievement)
                    .progress(0)
                    .isUnlocked(false)
                    .build();
        });

        record.setIsDeleted(false);
        record.setDeletedAt(null);
        record.setProgress(dto.getProgress());
        if (dto.getProgress() >= 100) {
            record.setIsUnlocked(true);
            if (record.getUnlockedAt() == null) {
                record.setUnlockedAt(Instant.now());
            }
        }

        return UserAchievementResponseDTO.fromModel(userAchievementRepository.save(record));
    }

    private UserModel requireActiveUser(UUID userId) {
        return userRepository.findById(userId)
                .filter(user -> !Boolean.TRUE.equals(user.getIsDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con ID: " + userId));
    }
}

