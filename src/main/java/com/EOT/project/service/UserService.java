package com.EOT.project.service;

import com.EOT.project.dto.request.UserRequestDTO;
import com.EOT.project.dto.response.UserResponseDTO;
import com.EOT.project.exception.BadRequestException;
import com.EOT.project.exception.ResourceNotFoundException;
import com.EOT.project.model.UserModel;
import com.EOT.project.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<UserResponseDTO> findAll() {
        return userRepository.findByIsDeletedFalse()
                .stream()
                .map(UserResponseDTO::fromModel)
                .toList();
    }

    @Transactional(readOnly = true)
    public UserResponseDTO findById(UUID id) {
        UserModel user = userRepository.findById(id)
                .filter(u -> !u.getIsDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con ID: " + id));
        return UserResponseDTO.fromModel(user);
    }

    @Transactional
    public UserResponseDTO create(UserRequestDTO dto) {
        if (userRepository.existsByUsername(dto.getUsername())) {
            throw new BadRequestException("El nombre de usuario '" + dto.getUsername() + "' ya está en uso");
        }
        if (userRepository.existsByEmail(dto.getEmail())) {
            throw new BadRequestException("El correo '" + dto.getEmail() + "' ya está registrado");
        }

        UserModel user = UserModel.builder()
                .username(dto.getUsername())
                .email(dto.getEmail())
                .password(dto.getPassword())
                .profilePicture(dto.getProfilePicture())
                .build();

        return UserResponseDTO.fromModel(userRepository.save(user));
    }

    @Transactional
    public void delete(UUID id) {
        UserModel user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con ID: " + id));
        user.setIsDeleted(true);
        userRepository.save(user);
    }
}

