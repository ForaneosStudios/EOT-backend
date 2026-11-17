package com.EOT.project.service;

import com.EOT.project.model.UserModel;
import com.EOT.project.repository.UserRepository;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrentUserServiceTest {
    @Mock UserRepository users; @InjectMocks CurrentUserService service;
    @Test void permitsAdminAndRejectsUnrelatedUser() {
        UUID authId = UUID.randomUUID(), profileId = UUID.randomUUID(); when(users.findByAuthUserIdAndIsDeletedFalse(authId)).thenReturn(Optional.of(UserModel.builder().userId(profileId).build()));
        var user = new UsernamePasswordAuthenticationToken(authId.toString(), "x");
        assertThrows(ResponseStatusException.class, () -> service.requireOwnerOrAdmin(user, UUID.randomUUID()));
        var admin = new UsernamePasswordAuthenticationToken(authId.toString(), "x", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        assertDoesNotThrow(() -> service.requireOwnerOrAdmin(admin, UUID.randomUUID()));
    }
}
