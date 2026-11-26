package com.EOT.project.service;

import com.EOT.project.dto.request.CreateProfileRequest;
import com.EOT.project.dto.request.UpdateProfilePictureRequest;
import com.EOT.project.dto.response.UserResponseDTO;
import com.EOT.project.exception.BadRequestException;
import com.EOT.project.exception.ConflictException;
import com.EOT.project.concurrency.ClientIntent;
import com.EOT.project.exception.ResourceNotFoundException;
import com.EOT.project.exception.StaleWriteException;
import com.EOT.project.model.UserModel;
import com.EOT.project.profilephoto.ProfilePhotoStorage;
import com.EOT.project.repository.UserRepository;
import java.util.List;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final ProfilePhotoStorage profilePhotos;

    @Transactional(readOnly = true)
    public List<UserResponseDTO> findAll() {
        return userRepository.findByIsDeletedFalse().stream().map(UserResponseDTO::fromModel).toList();
    }

    @Transactional(readOnly = true)
    public UserResponseDTO findById(UUID id) {
        return userRepository.findById(id).filter(user -> !Boolean.TRUE.equals(user.getIsDeleted()))
                .map(UserResponseDTO::fromModel)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
    }

    @Transactional
    public UserResponseDTO createProfile(UUID authId, String email, CreateProfileRequest dto) {
        if (email == null || email.isBlank() || email.length() > 150) {
            throw new BadRequestException("La cuenta autenticada debe tener un correo valido");
        }
        if (userRepository.findByAuthUserIdAndIsDeletedFalse(authId).isPresent()) {
            throw new ConflictException("La cuenta ya tiene un perfil");
        }
        if (userRepository.existsByUsernameIgnoreCase(dto.username()) || userRepository.existsByEmail(email)) {
            // Existing profiles must be linked by an administrator, never by matching email alone.
            throw new ConflictException("El nombre o correo ya esta registrado");
        }
        UserModel user = UserModel.builder().authUserId(authId).username(dto.username())
                .email(email).profilePicture(dto.profilePicture()).build();
        return UserResponseDTO.fromModel(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public boolean isUsernameAvailable(String username) {
        return !userRepository.existsByUsernameIgnoreCase(username);
    }

    @Transactional
    public UserResponseDTO updateProfilePicture(UUID userId, UpdateProfilePictureRequest dto) {
        UserModel user = userRepository.findById(userId)
                .filter(existing -> !Boolean.TRUE.equals(existing.getIsDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        discardPhoto(user);
        user.setProfilePicture(dto.profilePicture());
        return UserResponseDTO.fromModel(userRepository.save(user));
    }

    /** Like {@link #updateProfilePicture} but rejects a change made before the last saved one. */
    @Transactional
    public UserResponseDTO updateProfilePictureIfNewer(UUID userId, UpdateProfilePictureRequest dto,
                                                       Instant intentAt) {
        UserModel user = userRepository.findByIdForUpdate(userId)
                .filter(existing -> !Boolean.TRUE.equals(existing.getIsDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        if (ClientIntent.isStale(intentAt, user.getUpdatedAt(), user.getCreatedAt())) {
            throw new StaleWriteException("Otro cambio mas reciente de tu perfil ya se aplico",
                    UserResponseDTO.fromModel(user));
        }
        discardPhoto(user);
        user.setProfilePicture(dto.profilePicture());
        return UserResponseDTO.fromModel(userRepository.save(user));
    }

    @Transactional
    public void delete(UUID id) {
        UserModel user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        discardPhoto(user);
        user.setIsDeleted(true);
        user.setDeletedAt(Instant.now());
        userRepository.save(user);
    }

    /** Choosing a catalog avatar, or leaving, drops the camera photo: it is not kept without a purpose. */
    private void discardPhoto(UserModel user) {
        if (user.getProfilePhotoConsentAt() != null) {
            profilePhotos.delete(user.getUserId());
            user.setProfilePhotoConsentAt(null);
        }
    }
}
