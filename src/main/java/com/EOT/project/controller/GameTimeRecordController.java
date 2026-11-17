package com.EOT.project.controller;

import com.EOT.project.dto.request.GameTimeRecordRequest;
import com.EOT.project.dto.response.GameTimeRecordResponseDTO;
import com.EOT.project.model.UserModel;
import com.EOT.project.service.CurrentUserService;
import com.EOT.project.service.GameTimeRecordService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/time-records")
@RequiredArgsConstructor
public class GameTimeRecordController {
    private final GameTimeRecordService timeRecords;
    private final CurrentUserService currentUserService;

    @GetMapping("/me")
    public GameTimeRecordResponseDTO getCurrentUserRecord(Authentication authentication) {
        return timeRecords.get(currentUserService.requireProfile(authentication));
    }

    @PostMapping
    public ResponseEntity<GameTimeRecordResponseDTO> create(@Valid @RequestBody GameTimeRecordRequest request,
                                                              Authentication authentication) {
        return ResponseEntity.ok(timeRecords.submit(
                currentUserService.requireProfile(authentication), request.timeMs()));
    }

    @PutMapping("/me")
    public GameTimeRecordResponseDTO update(@Valid @RequestBody GameTimeRecordRequest request,
                                             Authentication authentication) {
        return timeRecords.submit(currentUserService.requireProfile(authentication), request.timeMs());
    }
}
