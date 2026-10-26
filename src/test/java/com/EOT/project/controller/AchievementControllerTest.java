package com.EOT.project.controller;

import com.EOT.project.dto.request.AchievementRequestDTO;
import com.EOT.project.dto.response.AchievementResponseDTO;
import com.EOT.project.exception.ResourceNotFoundException;
import com.EOT.project.service.AchievementService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AchievementControllerTest {

    @Mock
    private AchievementService achievementService;

    @InjectMocks
    private AchievementController controller;

    @Test
    void returnsOnlyPublicAchievementsByDefault() {
        List<AchievementResponseDTO> achievements = List.of(achievement(false));
        when(achievementService.findPublicAchievements()).thenReturn(achievements);

        ResponseEntity<List<AchievementResponseDTO>> response = controller.getAchievements(false, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(achievements, response.getBody());
        verify(achievementService).findPublicAchievements();
        verify(achievementService, never()).findAll();
    }

    @Test
    void returnsHiddenAchievementsToAdministrator() {
        Authentication admin = authentication("ROLE_ADMIN");
        List<AchievementResponseDTO> achievements = List.of(achievement(true));
        when(achievementService.findAll()).thenReturn(achievements);

        ResponseEntity<List<AchievementResponseDTO>> response = controller.getAchievements(true, admin);

        assertSame(achievements, response.getBody());
        verify(achievementService).findAll();
    }

    @Test
    void rejectsHiddenAchievementListWithoutAuthentication() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> controller.getAchievements(true, null));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        verify(achievementService, never()).findAll();
    }

    @Test
    void rejectsHiddenAchievementListForRegularUser() {
        Authentication user = authentication("ROLE_USER");

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> controller.getAchievements(true, user));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    @Test
    void returnsPublicAchievementWithoutAuthentication() {
        UUID achievementId = UUID.randomUUID();
        AchievementResponseDTO achievement = achievement(false);
        when(achievementService.findById(achievementId)).thenReturn(achievement);

        ResponseEntity<AchievementResponseDTO> response = controller.getAchievementById(achievementId, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(achievement, response.getBody());
    }

    @Test
    void rejectsHiddenAchievementForRegularUser() {
        UUID achievementId = UUID.randomUUID();
        Authentication user = authentication("ROLE_USER");
        when(achievementService.findById(achievementId)).thenReturn(achievement(true));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> controller.getAchievementById(achievementId, user));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    @Test
    void returnsHiddenAchievementToAdministrator() {
        UUID achievementId = UUID.randomUUID();
        Authentication admin = authentication("ROLE_ADMIN");
        AchievementResponseDTO achievement = achievement(true);
        when(achievementService.findById(achievementId)).thenReturn(achievement);

        ResponseEntity<AchievementResponseDTO> response = controller.getAchievementById(achievementId, admin);

        assertSame(achievement, response.getBody());
    }

    @Test
    void createsAchievementThroughService() {
        AchievementRequestDTO request = AchievementRequestDTO.builder()
                .achName("Explorador")
                .achDescription("Descubre una zona")
                .build();
        AchievementResponseDTO created = achievement(false);
        when(achievementService.create(request)).thenReturn(created);

        ResponseEntity<AchievementResponseDTO> response = controller.createAchievement(request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertSame(created, response.getBody());
    }

    @Test
    void propagatesMissingAchievementError() {
        UUID achievementId = UUID.randomUUID();
        when(achievementService.findById(achievementId))
                .thenThrow(new ResourceNotFoundException("Logro no encontrado"));

        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class,
                () -> controller.getAchievementById(achievementId, null));

        assertEquals("Logro no encontrado", exception.getMessage());
    }

    private AchievementResponseDTO achievement(boolean hidden) {
        return AchievementResponseDTO.builder()
                .achId(UUID.randomUUID())
                .achName("Explorador")
                .isAchHidden(hidden)
                .build();
    }

    private Authentication authentication(String role) {
        return new UsernamePasswordAuthenticationToken(
                "user", "token", List.of(new SimpleGrantedAuthority(role)));
    }
}
