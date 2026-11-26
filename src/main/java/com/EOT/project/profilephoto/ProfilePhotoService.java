package com.EOT.project.profilephoto;

import com.EOT.project.concurrency.ClientIntent;
import com.EOT.project.dto.request.UpdateProfilePhotoRequest;
import com.EOT.project.dto.response.UserResponseDTO;
import com.EOT.project.exception.BadRequestException;
import com.EOT.project.exception.ResourceNotFoundException;
import com.EOT.project.exception.StaleWriteException;
import com.EOT.project.model.UserModel;
import com.EOT.project.repository.UserRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Sets, replaces and removes a player's camera photo (kept in storage; its URL lives in the profile). */
@Service
@RequiredArgsConstructor
public class ProfilePhotoService {
    private final UserRepository users;
    private final ProfilePhotoProcessor processor;
    private final ProfilePhotoStorage storage;

    @Transactional
    public UserResponseDTO replace(UUID userId, UpdateProfilePhotoRequest request) {
        return store(activeUser(users.findById(userId).orElse(null)), request);
    }

    /** Like {@link #replace} but rejects a change made before the profile's last saved one. */
    @Transactional
    public UserResponseDTO replaceIfNewer(UUID userId, UpdateProfilePhotoRequest request, Instant intentAt) {
        UserModel user = activeUser(users.findByIdForUpdate(userId).orElse(null));
        if (ClientIntent.isStale(intentAt, user.getUpdatedAt(), user.getCreatedAt())) {
            throw new StaleWriteException("Otro cambio mas reciente de tu perfil ya se aplico", UserResponseDTO.fromModel(user));
        }
        return store(user, request);
    }

    @Transactional
    public UserResponseDTO remove(UUID userId) {
        return release(activeUser(users.findById(userId).orElse(null)));
    }

    /** Moderation: an administrator removes the photo of any account. */
    @Transactional
    public UserResponseDTO removeAsAdmin(UUID targetUserId) {
        return release(activeUser(users.findById(targetUserId).orElse(null)));
    }

    private UserResponseDTO store(UserModel user, UpdateProfilePhotoRequest request) {
        if (!Boolean.TRUE.equals(request.consent())) {
            throw new BadRequestException("Debes aceptar que tu foto sea visible para otros jugadores");
        }
        byte[] jpeg = processor.process(request.image());
        user.setProfilePicture(storage.upload(user.getUserId(), jpeg));
        user.setProfilePhotoConsentAt(Instant.now());
        return UserResponseDTO.fromModel(users.save(user));
    }

    private UserResponseDTO release(UserModel user) {
        if (user.getProfilePhotoConsentAt() != null) {
            storage.delete(user.getUserId());
            user.setProfilePicture(null);
            user.setProfilePhotoConsentAt(null);
            users.save(user);
        }
        return UserResponseDTO.fromModel(user);
    }

    private static UserModel activeUser(UserModel user) {
        if (user == null || Boolean.TRUE.equals(user.getIsDeleted())) {
            throw new ResourceNotFoundException("Usuario no encontrado");
        }
        return user;
    }
}
