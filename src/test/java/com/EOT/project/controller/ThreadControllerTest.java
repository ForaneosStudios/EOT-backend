package com.EOT.project.controller;

import com.EOT.project.dto.request.ThreadRequestDTO;
import com.EOT.project.dto.response.ThreadResponseDTO;
import com.EOT.project.exception.ResourceNotFoundException;
import com.EOT.project.service.CurrentUserService;
import com.EOT.project.service.ThreadService;
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
class ThreadControllerTest {

    @Mock
    private ThreadService threadService;

    @Mock
    private CurrentUserService currentUserService;

    @InjectMocks
    private ThreadController controller;

    @Test
    void returnsAllThreadsWhenCategoryIsNotProvided() {
        List<ThreadResponseDTO> threads = List.of(ThreadResponseDTO.builder().title("General").build());
        when(threadService.findAll()).thenReturn(threads);

        ResponseEntity<List<ThreadResponseDTO>> response = controller.getThreads(null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(threads, response.getBody());
        verify(threadService).findAll();
    }

    @Test
    void filtersThreadsByCategory() {
        UUID categoryId = UUID.randomUUID();
        List<ThreadResponseDTO> threads = List.of(ThreadResponseDTO.builder().categoryId(categoryId).build());
        when(threadService.findByCategoryId(categoryId)).thenReturn(threads);

        ResponseEntity<List<ThreadResponseDTO>> response = controller.getThreads(categoryId);

        assertSame(threads, response.getBody());
        verify(threadService).findByCategoryId(categoryId);
        verify(threadService, never()).findAll();
    }

    @Test
    void returnsThreadById() {
        UUID threadId = UUID.randomUUID();
        ThreadResponseDTO thread = ThreadResponseDTO.builder().threadId(threadId).build();
        when(threadService.findById(threadId)).thenReturn(thread);

        ResponseEntity<ThreadResponseDTO> response = controller.getThreadById(threadId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(thread, response.getBody());
    }

    @Test
    void createsUnlockedThreadForItsOwner() {
        ThreadRequestDTO request = request(false);
        ThreadResponseDTO created = ThreadResponseDTO.builder().title(request.getTitle()).build();
        Authentication user = authentication("ROLE_USER");
        when(threadService.create(request)).thenReturn(created);

        ResponseEntity<ThreadResponseDTO> response = controller.createThread(request, user);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertSame(created, response.getBody());
        verify(currentUserService).requireOwnerOrAdmin(user, request.getUserId());
        verify(threadService).create(request);
    }

    @Test
    void rejectsLockedThreadFromRegularUser() {
        ThreadRequestDTO request = request(true);
        Authentication user = authentication("ROLE_USER");

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> controller.createThread(request, user));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        verify(currentUserService).requireOwnerOrAdmin(user, request.getUserId());
        verify(threadService, never()).create(request);
    }

    @Test
    void allowsAdministratorToCreateLockedThread() {
        ThreadRequestDTO request = request(true);
        Authentication admin = authentication("ROLE_ADMIN");
        ThreadResponseDTO created = ThreadResponseDTO.builder().isLocked(true).build();
        when(threadService.create(request)).thenReturn(created);

        ResponseEntity<ThreadResponseDTO> response = controller.createThread(request, admin);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertSame(created, response.getBody());
        verify(threadService).create(request);
    }

    @Test
    void propagatesServiceErrorsWhenThreadDoesNotExist() {
        UUID threadId = UUID.randomUUID();
        when(threadService.findById(threadId)).thenThrow(new ResourceNotFoundException("Hilo no encontrado"));

        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class,
                () -> controller.getThreadById(threadId));

        assertEquals("Hilo no encontrado", exception.getMessage());
    }

    private ThreadRequestDTO request(boolean locked) {
        return ThreadRequestDTO.builder()
                .categoryId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .title("Nuevo hilo")
                .content("Contenido del hilo")
                .isLocked(locked)
                .build();
    }

    private Authentication authentication(String role) {
        return new UsernamePasswordAuthenticationToken(
                "user", "token", List.of(new SimpleGrantedAuthority(role)));
    }
}
