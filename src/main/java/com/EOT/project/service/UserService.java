package com.EOT.project.service;

import com.EOT.project.dto.request.UserRequestDTO;
import com.EOT.project.dto.request.UserLoginRequestDTO;
import com.EOT.project.dto.response.UserResponseDTO;
import com.EOT.project.exception.BadRequestException;
import com.EOT.project.exception.ResourceNotFoundException;
import com.EOT.project.model.UserModel;
import com.EOT.project.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

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
                .password(passwordEncoder.encode(dto.getPassword()))
                .profilePicture(dto.getProfilePicture())
                .build();

        return UserResponseDTO.fromModel(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public UserResponseDTO login(UserLoginRequestDTO dto) {
        UserModel user = userRepository.findByEmailAndIsDeletedFalse(dto.email())
                .orElseThrow(this::invalidCredentials);

        if (!passwordEncoder.matches(dto.password(), user.getPassword())) {
            throw invalidCredentials();
        }

        return UserResponseDTO.fromModel(user);
    }

    private ResponseStatusException invalidCredentials() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Correo o contraseña incorrectos");
    }

    @Transactional
    public void delete(UUID id) {
        UserModel user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con ID: " + id));
        user.setIsDeleted(true);
        userRepository.save(user);
    }
}
