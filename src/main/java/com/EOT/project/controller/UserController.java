package com.EOT.project.controller;

import com.EOT.project.dto.request.CreateProfileRequest;
import com.EOT.project.dto.response.UserResponseDTO;
import com.EOT.project.service.CurrentUserService;
import com.EOT.project.service.UserService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;
    private final CurrentUserService currentUserService;

    @GetMapping
    public List<UserResponseDTO> getAllUsers() { return userService.findAll(); }

    @GetMapping("/{id}")
    public UserResponseDTO getUserById(@PathVariable UUID id) { return userService.findById(id); }

    @GetMapping("/me")
    public UserResponseDTO currentUser(Authentication authentication) {
        return UserResponseDTO.fromModel(currentUserService.requireProfile(authentication));
    }

    @PostMapping("/me/profile")
    public ResponseEntity<UserResponseDTO> createProfile(Authentication authentication,
                                                          @Valid @RequestBody CreateProfileRequest dto) {
        Jwt token = (Jwt) authentication.getPrincipal();
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.createProfile(
                UUID.fromString(token.getSubject()), token.getClaimAsString("email"), dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable UUID id) {
        userService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
