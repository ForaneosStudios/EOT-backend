package com.EOT.project.controller;

import com.EOT.project.dto.request.CreatePrivateConversationRequest;
import com.EOT.project.dto.request.SendPrivateMessageRequest;
import com.EOT.project.dto.response.PrivateConversationResponseDTO;
import com.EOT.project.dto.response.PrivateMessageResponseDTO;
import com.EOT.project.service.CurrentUserService;
import com.EOT.project.service.PrivateConversationService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/private-conversations")
@RequiredArgsConstructor
public class PrivateConversationController {
    private final PrivateConversationService conversationService;
    private final CurrentUserService currentUserService;

    @GetMapping
    public List<PrivateConversationResponseDTO> getConversations(Authentication authentication) {
        return conversationService.findAllFor(currentUserService.requireProfile(authentication));
    }

    @PostMapping
    public ResponseEntity<PrivateConversationResponseDTO> createConversation(
            @Valid @RequestBody CreatePrivateConversationRequest request, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(conversationService.createOrFind(request.recipientId(),
                currentUserService.requireProfile(authentication)));
    }

    @GetMapping("/{conversationId}")
    public PrivateConversationResponseDTO getConversation(@PathVariable UUID conversationId, Authentication authentication) {
        return conversationService.findById(conversationId, currentUserService.requireProfile(authentication));
    }

    @GetMapping("/{conversationId}/messages")
    public Page<PrivateMessageResponseDTO> getMessages(@PathVariable UUID conversationId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "30") int size,
            Authentication authentication) {
        return conversationService.findMessages(conversationId, currentUserService.requireProfile(authentication),
                PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100)));
    }

    @PostMapping("/{conversationId}/messages")
    public ResponseEntity<PrivateMessageResponseDTO> sendMessage(@PathVariable UUID conversationId,
            @Valid @RequestBody SendPrivateMessageRequest request, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(conversationService.sendMessage(conversationId,
                request.content(), currentUserService.requireProfile(authentication)));
    }

    @PatchMapping("/{conversationId}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable UUID conversationId, Authentication authentication) {
        conversationService.markAsRead(conversationId, currentUserService.requireProfile(authentication));
        return ResponseEntity.noContent().build();
    }
}
