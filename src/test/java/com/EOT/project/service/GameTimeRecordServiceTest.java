package com.EOT.project.service;

import com.EOT.project.dto.response.GameTimeRecordResponseDTO;
import com.EOT.project.model.UserModel;
import com.EOT.project.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GameTimeRecordServiceTest {

    @Mock
    private UserRepository users;

    @Mock
    private org.springframework.context.ApplicationEventPublisher events;

    @InjectMocks
    private GameTimeRecordService service;

    @Test
    void storesTheFirstSubmittedTimeForTheAuthenticatedPlayer() {
        UUID userId = UUID.randomUUID();
        UserModel user = UserModel.builder().userId(userId).build();

        GameTimeRecordResponseDTO result = service.submit(user, 92_345L);

        assertEquals(userId, result.userId());
        assertEquals(92_345L, result.timeMs());
        verify(users).save(user);
    }

    @Test
    void replacesTheBestTimeOnlyWhenTheNewRunIsFaster() {
        UserModel user = UserModel.builder().userId(UUID.randomUUID()).bestTimeMs(10_000L).build();

        GameTimeRecordResponseDTO result = service.submit(user, 9_000L);

        assertEquals(9_000L, result.timeMs());
        verify(users).save(user);
    }

    @Test
    void preservesTheBestTimeWhenTheNewRunIsSlower() {
        UserModel user = UserModel.builder().userId(UUID.randomUUID()).bestTimeMs(9_000L).build();

        GameTimeRecordResponseDTO result = service.submit(user, 10_000L);

        assertEquals(9_000L, result.timeMs());
        verify(users, never()).save(user);
    }
}
