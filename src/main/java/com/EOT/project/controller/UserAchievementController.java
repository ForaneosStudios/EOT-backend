package com.EOT.project.controller;

import com.EOT.project.dto.request.UserAchievementProgressDTO;
import com.EOT.project.dto.response.UserAchievementResponseDTO;
import com.EOT.project.service.UserAchievementService;
import com.EOT.project.service.CurrentUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/user-achievements")
@RequiredArgsConstructor
public class UserAchievementController {

    private final UserAchievementService userAchievementService;
    private final CurrentUserService currentUserService;

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<UserAchievementResponseDTO>> getUserAchievements(@PathVariable UUID userId, Authentication authentication) {
        currentUserService.requireOwnerOrAdmin(authentication, userId);
        return ResponseEntity.ok(userAchievementService.findByUserId(userId));
    }

    @PutMapping("/progress")
    public ResponseEntity<UserAchievementResponseDTO> updateAchievementProgress(
            @Valid @RequestBody UserAchievementProgressDTO dto, Authentication authentication) {
        currentUserService.requireOwnerOrAdmin(authentication, dto.getUserId());
        return ResponseEntity.ok(userAchievementService.updateProgress(dto));
    }
}

