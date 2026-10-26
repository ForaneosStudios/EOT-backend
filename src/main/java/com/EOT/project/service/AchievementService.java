package com.EOT.project.service;

import com.EOT.project.dto.request.AchievementRequestDTO;
import com.EOT.project.dto.response.AchievementResponseDTO;
import com.EOT.project.exception.ConflictException;
import com.EOT.project.exception.ResourceNotFoundException;
import com.EOT.project.model.AchievementModel;
import com.EOT.project.repository.AchievementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AchievementService {

    private final AchievementRepository achievementRepository;

    @Transactional(readOnly = true)
    public List<AchievementResponseDTO> findAll() {
        return achievementRepository.findByIsDeletedFalse()
                .stream()
                .map(AchievementResponseDTO::fromModel)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AchievementResponseDTO> findPublicAchievements() {
        return achievementRepository.findByIsAchHiddenFalseAndIsDeletedFalse()
                .stream()
                .map(AchievementResponseDTO::fromModel)
                .toList();
    }

    @Transactional(readOnly = true)
    public AchievementResponseDTO findById(UUID id) {
        return AchievementResponseDTO.fromModel(requireActive(id));
    }

    @Transactional
    public AchievementResponseDTO create(AchievementRequestDTO dto) {
        if (achievementRepository.findByAchName(dto.getAchName()).isPresent()) {
            throw new ConflictException("El logro '" + dto.getAchName() + "' ya existe");
        }

        AchievementModel achievement = AchievementModel.builder()
                .achName(dto.getAchName())
                .achDescription(dto.getAchDescription())
                .achPicture(dto.getAchPicture())
                .isAchHidden(dto.getIsAchHidden() != null ? dto.getIsAchHidden() : false)
                .build();

        return AchievementResponseDTO.fromModel(achievementRepository.save(achievement));
    }

    @Transactional
    public AchievementResponseDTO update(UUID id, AchievementRequestDTO dto) {
        AchievementModel achievement = requireActive(id);
        achievementRepository.findByAchName(dto.getAchName())
                .filter(existing -> !existing.getAchId().equals(id))
                .ifPresent(existing -> {
                    throw new ConflictException("El logro '" + dto.getAchName() + "' ya existe");
                });
        achievement.setAchName(dto.getAchName());
        achievement.setAchDescription(dto.getAchDescription());
        achievement.setAchPicture(dto.getAchPicture());
        achievement.setIsAchHidden(Boolean.TRUE.equals(dto.getIsAchHidden()));
        return AchievementResponseDTO.fromModel(achievementRepository.save(achievement));
    }

    @Transactional
    public void delete(UUID id) {
        AchievementModel achievement = requireActive(id);
        achievement.setIsDeleted(true);
        achievement.setDeletedAt(Instant.now());
        achievementRepository.save(achievement);
    }

    private AchievementModel requireActive(UUID id) {
        return achievementRepository.findById(id)
                .filter(achievement -> !Boolean.TRUE.equals(achievement.getIsDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Logro no encontrado con ID: " + id));
    }
}

