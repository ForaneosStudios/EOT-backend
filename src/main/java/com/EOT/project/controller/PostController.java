package com.EOT.project.controller;

import com.EOT.project.dto.request.PostRequestDTO;
import com.EOT.project.dto.response.PostResponseDTO;
import com.EOT.project.service.PostService;
import com.EOT.project.service.CurrentUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;
    private final CurrentUserService currentUserService;

    @GetMapping
    public ResponseEntity<List<PostResponseDTO>> getPostsByThread(@RequestParam UUID threadId) {
        return ResponseEntity.ok(postService.findByThreadId(threadId));
    }

    @PostMapping
    public ResponseEntity<PostResponseDTO> createPost(@Valid @RequestBody PostRequestDTO dto, Authentication authentication) {
        currentUserService.requireOwnerOrAdmin(authentication, dto.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(postService.create(dto));
    }
}

