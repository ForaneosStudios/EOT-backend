package com.EOT.project.service;

import com.EOT.project.model.UserModel;
import com.EOT.project.repository.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class CurrentUserService {
    private final UserRepository users;

    public UserModel requireProfile(Authentication authentication) {
        UUID id = UUID.fromString(authentication.getName());
        return users.findByAuthUserIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Perfil no disponible"));
    }

    public void requireOwnerOrAdmin(Authentication authentication, UUID userId) {
        UserModel current = requireProfile(authentication);
        if (!isAdmin(authentication) && !current.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes permiso para esta operacion");
        }
    }

    public boolean isAdmin(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
    }
}
