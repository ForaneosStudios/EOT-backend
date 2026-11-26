package com.EOT.project.controller;

import com.EOT.project.concurrency.ClientIntent;
import java.time.Instant;
import com.EOT.project.dto.request.CreateProfileRequest;
import com.EOT.project.dto.request.UpdateProfilePhotoRequest;
import com.EOT.project.dto.request.UpdateProfilePictureRequest;
import com.EOT.project.profilephoto.ProfilePhotoService;
import com.EOT.project.dto.response.UserResponseDTO;
import com.EOT.project.dto.response.UsernameAvailabilityResponse;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Validated
public class UserController {
    private final UserService userService;
    private final CurrentUserService currentUserService;
    private final ProfilePhotoService profilePhotos;

    @GetMapping
    public List<UserResponseDTO> getAllUsers() { return userService.findAll(); }

    @GetMapping("/{id}")
    public UserResponseDTO getUserById(@PathVariable UUID id) { return userService.findById(id); }

    @GetMapping("/me")
    public UserResponseDTO currentUser(Authentication authentication) {
        return UserResponseDTO.fromModel(currentUserService.requireProfile(authentication));
    }

    @GetMapping("/availability")
    public UsernameAvailabilityResponse usernameAvailability(
            @RequestParam @Size(min = 4, max = 50) @Pattern(regexp = "[a-zA-Z0-9_]+") String username) {
        return new UsernameAvailabilityResponse(userService.isUsernameAvailable(username));
    }

    @PostMapping("/me/profile")
    public ResponseEntity<UserResponseDTO> createProfile(Authentication authentication,
                                                          @Valid @RequestBody CreateProfileRequest dto) {
        Jwt token = (Jwt) authentication.getPrincipal();
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.createProfile(
                UUID.fromString(token.getSubject()), token.getClaimAsString("email"), dto));
    }

    @PatchMapping("/me/profile-picture")
    public UserResponseDTO updateProfilePicture(Authentication authentication,
                                                 @Valid @RequestBody UpdateProfilePictureRequest dto) {
        UUID userId = currentUserService.requireProfile(authentication).getUserId();
        Instant intentAt = ClientIntent.current();
        return intentAt == null ? userService.updateProfilePicture(userId, dto)
                : userService.updateProfilePictureIfNewer(userId, dto, intentAt);
    }

    @PutMapping("/me/profile-photo")
    public UserResponseDTO updateProfilePhoto(Authentication authentication,
                                              @Valid @RequestBody UpdateProfilePhotoRequest dto) {
        UUID userId = currentUserService.requireProfile(authentication).getUserId();
        Instant intentAt = ClientIntent.current();
        return intentAt == null ? profilePhotos.replace(userId, dto)
                : profilePhotos.replaceIfNewer(userId, dto, intentAt);
    }

    @DeleteMapping("/me/profile-photo")
    public UserResponseDTO removeProfilePhoto(Authentication authentication) {
        return profilePhotos.remove(currentUserService.requireProfile(authentication).getUserId());
    }

    /** Moderation (administrators only, through the /users/** rule). */
    @DeleteMapping("/{id}/profile-photo")
    public UserResponseDTO removeProfilePhotoOf(@PathVariable UUID id) {
        return profilePhotos.removeAsAdmin(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable UUID id) {
        userService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
