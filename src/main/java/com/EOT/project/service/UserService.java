package com.EOT.project.service;

import com.EOT.project.dto.request.CreateProfileRequest;
import com.EOT.project.dto.response.UserResponseDTO;
import com.EOT.project.exception.BadRequestException;
import com.EOT.project.exception.ResourceNotFoundException;
import com.EOT.project.model.UserModel;
import com.EOT.project.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<UserResponseDTO> findAll() {
        return userRepository.findByIsDeletedFalse().stream().map(UserResponseDTO::fromModel).toList();
    }

    @Transactional(readOnly = true)
    public UserResponseDTO findById(UUID id) {
        return userRepository.findById(id).filter(user -> !user.getIsDeleted())
                .map(UserResponseDTO::fromModel)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
    }

    @Transactional
    public UserResponseDTO createProfile(UUID authId, String email, CreateProfileRequest dto) {
        if (email == null || email.isBlank() || email.length() > 150) {
            throw new BadRequestException("La cuenta autenticada debe tener un correo valido");
        }
        if (userRepository.findByAuthUserIdAndIsDeletedFalse(authId).isPresent()) {
            throw new BadRequestException("La cuenta ya tiene un perfil");
        }
        if (userRepository.existsByUsername(dto.username()) || userRepository.existsByEmail(email)) {
            // Existing profiles must be linked by an administrator, never by matching email alone.
            throw new BadRequestException("El nombre o correo ya esta registrado");
        }
        UserModel user = UserModel.builder().authUserId(authId).username(dto.username())
                .email(email).profilePicture(dto.profilePicture()).build();
        return UserResponseDTO.fromModel(userRepository.save(user));
    }

    @Transactional
    public void delete(UUID id) {
        UserModel user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        user.setIsDeleted(true);
        userRepository.save(user);
    }
}
