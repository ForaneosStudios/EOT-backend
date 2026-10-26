package com.EOT.project.controller;

import com.EOT.project.dto.request.PostRequestDTO;
import com.EOT.project.dto.request.PostUpdateRequestDTO;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    @GetMapping("/{id}")
    public ResponseEntity<PostResponseDTO> getPostById(@PathVariable UUID id) {
        return ResponseEntity.ok(postService.findById(id));
    }

    @PostMapping
    public ResponseEntity<PostResponseDTO> createPost(@Valid @RequestBody PostRequestDTO dto, Authentication authentication) {
        currentUserService.requireOwnerOrAdmin(authentication, dto.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(postService.create(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PostResponseDTO> updatePost(@PathVariable UUID id,
                                                       @Valid @RequestBody PostUpdateRequestDTO dto,
                                                       Authentication authentication) {
        PostResponseDTO existing = postService.findById(id);
        currentUserService.requireOwnerOrAdmin(authentication, existing.getUserId());
        return ResponseEntity.ok(postService.update(id, dto, currentUserService.isAdmin(authentication)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePost(@PathVariable UUID id, Authentication authentication) {
        PostResponseDTO existing = postService.findById(id);
        currentUserService.requireOwnerOrAdmin(authentication, existing.getUserId());
        postService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

