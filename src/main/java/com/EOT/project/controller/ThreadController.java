package com.EOT.project.controller;

import com.EOT.project.dto.request.ThreadRequestDTO;
import com.EOT.project.dto.request.ThreadLockRequestDTO;
import com.EOT.project.dto.request.ThreadUpdateRequestDTO;
import com.EOT.project.dto.response.ThreadResponseDTO;
import com.EOT.project.service.ThreadService;
import com.EOT.project.service.CurrentUserService;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/threads")
@RequiredArgsConstructor
public class ThreadController {

    private final ThreadService threadService;
    private final CurrentUserService currentUserService;

    @GetMapping
    public ResponseEntity<List<ThreadResponseDTO>> getThreads(@RequestParam(required = false) UUID categoryId) {
        if (categoryId != null) {
            return ResponseEntity.ok(threadService.findByCategoryId(categoryId));
        }
        return ResponseEntity.ok(threadService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ThreadResponseDTO> getThreadById(@PathVariable UUID id) {
        return ResponseEntity.ok(threadService.findById(id));
    }

    @PostMapping
    public ResponseEntity<ThreadResponseDTO> createThread(@Valid @RequestBody ThreadRequestDTO dto, Authentication authentication) {
        currentUserService.requireOwnerOrAdmin(authentication, dto.getUserId());
        if (Boolean.TRUE.equals(dto.getIsLocked()) && authentication.getAuthorities().stream()
                .noneMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo un administrador puede bloquear un hilo");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(threadService.create(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ThreadResponseDTO> updateThread(@PathVariable UUID id,
                                                           @Valid @RequestBody ThreadUpdateRequestDTO dto,
                                                           Authentication authentication) {
        ThreadResponseDTO existing = threadService.findById(id);
        currentUserService.requireOwnerOrAdmin(authentication, existing.getUserId());
        if (Boolean.TRUE.equals(existing.getIsLocked()) && !currentUserService.isAdmin(authentication)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Un hilo bloqueado solo puede ser editado por un administrador");
        }
        return ResponseEntity.ok(threadService.update(id, dto));
    }

    @PatchMapping("/{id}/lock")
    public ResponseEntity<ThreadResponseDTO> setThreadLocked(@PathVariable UUID id,
                                                              @Valid @RequestBody ThreadLockRequestDTO dto) {
        return ResponseEntity.ok(threadService.setLocked(id, dto.locked()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteThread(@PathVariable UUID id, Authentication authentication) {
        ThreadResponseDTO existing = threadService.findById(id);
        currentUserService.requireOwnerOrAdmin(authentication, existing.getUserId());
        threadService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

