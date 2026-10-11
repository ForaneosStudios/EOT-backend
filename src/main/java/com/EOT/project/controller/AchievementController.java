package com.EOT.project.controller;

import com.EOT.project.dto.request.AchievementRequestDTO;
import com.EOT.project.dto.response.AchievementResponseDTO;
import com.EOT.project.service.AchievementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
    public ResponseEntity<List<AchievementResponseDTO>> getAchievements(@RequestParam(defaultValue = "false") boolean includeHidden) {
        if (includeHidden) {
            return ResponseEntity.ok(achievementService.findAll());
        }
        return ResponseEntity.ok(achievementService.findPublicAchievements());
    }

    @GetMapping("/{id}")
    public ResponseEntity<AchievementResponseDTO> getAchievementById(@PathVariable UUID id) {
        return ResponseEntity.ok(achievementService.findById(id));
    }

    @PostMapping
    public ResponseEntity<AchievementResponseDTO> createAchievement(@Valid @RequestBody AchievementRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(achievementService.create(dto));
    }
}

