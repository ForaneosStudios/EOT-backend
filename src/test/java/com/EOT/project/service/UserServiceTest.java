package com.EOT.project.service;

import com.EOT.project.dto.request.CreateProfileRequest;
import com.EOT.project.dto.response.UserResponseDTO;
import com.EOT.project.exception.BadRequestException;
import com.EOT.project.exception.ConflictException;
import com.EOT.project.exception.ResourceNotFoundException;
import com.EOT.project.model.UserModel;
import com.EOT.project.model.UserRole;
import com.EOT.project.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @Test
    void returnsOnlyActiveUsersProvidedByRepository() {
        UserModel first = user("first", "first@example.com");
        UserModel second = user("second", "second@example.com");
        when(userRepository.findByIsDeletedFalse()).thenReturn(List.of(first, second));

        List<UserResponseDTO> result = userService.findAll();

        assertEquals(2, result.size());
        assertEquals("first", result.getFirst().getUsername());
        assertEquals("second", result.getLast().getUsername());
    }

    @Test
    void returnsActiveUserById() {
        UUID userId = UUID.randomUUID();
        UserModel user = user("active", "active@example.com");
        user.setUserId(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        UserResponseDTO result = userService.findById(userId);

        assertEquals(userId, result.getUserId());
        assertEquals("active", result.getUsername());
    }

    @Test
    void treatsDeletedUserAsMissing() {
        UUID userId = UUID.randomUUID();
        UserModel deleted = user("deleted", "deleted@example.com");
        deleted.setIsDeleted(true);
        when(userRepository.findById(userId)).thenReturn(Optional.of(deleted));

        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class,
                () -> userService.findById(userId));

        assertEquals("Usuario no encontrado", exception.getMessage());
    }

    @Test
    void rejectsMissingUser() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> userService.findById(userId));
    }

    @Test
    void rejectsProfileWhenAuthenticatedEmailIsInvalid() {
        UUID authId = UUID.randomUUID();
        CreateProfileRequest request = new CreateProfileRequest("player", null);
        String oversizedEmail = "a".repeat(151);

        assertThrows(BadRequestException.class, () -> userService.createProfile(authId, null, request));
        assertThrows(BadRequestException.class, () -> userService.createProfile(authId, "   ", request));
        assertThrows(BadRequestException.class,
                () -> userService.createProfile(authId, oversizedEmail, request));
        verify(userRepository, never()).save(any());
    }

    @Test
    void rejectsSecondProfileForSameAuthenticatedAccount() {
        UUID authId = UUID.randomUUID();
        CreateProfileRequest request = new CreateProfileRequest("new_user", null);
        when(userRepository.findByAuthUserIdAndIsDeletedFalse(authId))
                .thenReturn(Optional.of(user("existing", "existing@example.com")));

        ConflictException exception = assertThrows(ConflictException.class,
                () -> userService.createProfile(authId, "new@example.com", request));

        assertEquals("La cuenta ya tiene un perfil", exception.getMessage());
        verify(userRepository, never()).save(any());
    }

    @Test
    void rejectsDuplicateUsername() {
        UUID authId = UUID.randomUUID();
        CreateProfileRequest request = new CreateProfileRequest("taken", null);
        when(userRepository.findByAuthUserIdAndIsDeletedFalse(authId)).thenReturn(Optional.empty());
        when(userRepository.existsByUsername("taken")).thenReturn(true);

        ConflictException exception = assertThrows(ConflictException.class,
                () -> userService.createProfile(authId, "new@example.com", request));

        assertEquals("El nombre o correo ya esta registrado", exception.getMessage());
        verify(userRepository, never()).existsByEmail("new@example.com");
    }

    @Test
    void rejectsDuplicateEmail() {
        UUID authId = UUID.randomUUID();
        CreateProfileRequest request = new CreateProfileRequest("new_user", null);
        when(userRepository.findByAuthUserIdAndIsDeletedFalse(authId)).thenReturn(Optional.empty());
        when(userRepository.existsByUsername("new_user")).thenReturn(false);
        when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

        assertThrows(ConflictException.class,
                () -> userService.createProfile(authId, "taken@example.com", request));
        verify(userRepository, never()).save(any());
    }

    @Test
    void createsProfileFromVerifiedIdentityWithDefaultUserRole() {
        UUID authId = UUID.randomUUID();
        CreateProfileRequest request = new CreateProfileRequest("new_user", "avatar.png");
        when(userRepository.findByAuthUserIdAndIsDeletedFalse(authId)).thenReturn(Optional.empty());
        when(userRepository.existsByUsername("new_user")).thenReturn(false);
        when(userRepository.existsByEmail("verified@example.com")).thenReturn(false);
        when(userRepository.save(any(UserModel.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponseDTO result = userService.createProfile(authId, "verified@example.com", request);

        ArgumentCaptor<UserModel> savedUser = ArgumentCaptor.forClass(UserModel.class);
        verify(userRepository).save(savedUser.capture());
        assertEquals(authId, savedUser.getValue().getAuthUserId());
        assertEquals("verified@example.com", result.getEmail());
        assertEquals("new_user", result.getUsername());
        assertEquals(UserRole.USER, result.getRole());
        assertEquals("avatar.png", result.getProfilePicture());
        assertNull(savedUser.getValue().getPassword());
    }

    @Test
    void softDeletesExistingUser() {
        UUID userId = UUID.randomUUID();
        UserModel existing = user("player", "player@example.com");
        when(userRepository.findById(userId)).thenReturn(Optional.of(existing));

        userService.delete(userId);

        assertTrue(existing.getIsDeleted());
        verify(userRepository).save(existing);
    }

    @Test
    void rejectsDeletionOfMissingUser() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> userService.delete(userId));

        verify(userRepository, never()).save(any());
    }

    private UserModel user(String username, String email) {
        UserModel user = UserModel.builder()
                .userId(UUID.randomUUID())
                .username(username)
                .email(email)
                .role(UserRole.USER)
                .build();
        assertFalse(user.getIsDeleted());
        return user;
    }
}
