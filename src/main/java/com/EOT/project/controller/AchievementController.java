package com.EOT.project.controller;

import com.EOT.project.dto.request.AchievementRequestDTO;
import com.EOT.project.dto.response.AchievementResponseDTO;
import com.EOT.project.service.AchievementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/achievements")
@RequiredArgsConstructor
public class AchievementController {

    private final AchievementService achievementService;

    @GetMapping
    public ResponseEntity<List<AchievementResponseDTO>> getAchievements(@RequestParam(defaultValue = "false") boolean includeHidden, Authentication authentication) {
        if (includeHidden) {
            requireAdmin(authentication);
            return ResponseEntity.ok(achievementService.findAll());
        }
        return ResponseEntity.ok(achievementService.findPublicAchievements());
    }

    @GetMapping("/{id}")
    public ResponseEntity<AchievementResponseDTO> getAchievementById(@PathVariable UUID id, Authentication authentication) {
        AchievementResponseDTO achievement = achievementService.findById(id);
        if (Boolean.TRUE.equals(achievement.getIsAchHidden())) {
            requireAdmin(authentication);
        }
        return ResponseEntity.ok(achievement);
    }

    @PostMapping
    public ResponseEntity<AchievementResponseDTO> createAchievement(@Valid @RequestBody AchievementRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(achievementService.create(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AchievementResponseDTO> updateAchievement(@PathVariable UUID id,
                                                                     @Valid @RequestBody AchievementRequestDTO dto) {
        return ResponseEntity.ok(achievementService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAchievement(@PathVariable UUID id) {
        achievementService.delete(id);
        return ResponseEntity.noContent().build();
    }

    private void requireAdmin(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities().stream()
                .noneMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Se requieren permisos de administrador");
        }
    }
}

