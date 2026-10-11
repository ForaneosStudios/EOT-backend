package com.EOT.project.service;

import com.EOT.project.dto.request.AchievementRequestDTO;
import com.EOT.project.dto.response.AchievementResponseDTO;
import com.EOT.project.exception.BadRequestException;
import com.EOT.project.exception.ResourceNotFoundException;
import com.EOT.project.model.AchievementModel;
import com.EOT.project.repository.AchievementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        AchievementModel ach = achievementRepository.findById(id)
                .filter(a -> !a.getIsDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Logro no encontrado con ID: " + id));
        return AchievementResponseDTO.fromModel(ach);
    }

    @Transactional
    public AchievementResponseDTO create(AchievementRequestDTO dto) {
        if (achievementRepository.findByAchName(dto.getAchName()).isPresent()) {
            throw new BadRequestException("El logro '" + dto.getAchName() + "' ya existe");
        }

        AchievementModel achievement = AchievementModel.builder()
                .achName(dto.getAchName())
                .achDescription(dto.getAchDescription())
                .achPicture(dto.getAchPicture())
                .isAchHidden(dto.getIsAchHidden() != null ? dto.getIsAchHidden() : false)
                .build();

        return AchievementResponseDTO.fromModel(achievementRepository.save(achievement));
    }
}

