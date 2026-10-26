package com.EOT.project.service;

import com.EOT.project.dto.request.UserAchievementProgressDTO;
import com.EOT.project.exception.BadRequestException;
import com.EOT.project.repository.AchievementRepository;
import com.EOT.project.repository.UserAchievementRepository;
import com.EOT.project.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class UserAchievementServiceTest {

    @Mock
    private UserAchievementRepository userAchievementRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AchievementRepository achievementRepository;

    @InjectMocks
    private UserAchievementService service;

    @Test
    void rejectsNullProgressBeforeAccessingPersistence() {
        UserAchievementProgressDTO request = requestWithProgress(null);

        assertThrows(BadRequestException.class, () -> service.updateProgress(request));

        verifyNoInteractions(userAchievementRepository, userRepository, achievementRepository);
    }

    @Test
    void rejectsProgressBelowAllowedRangeBeforeAccessingPersistence() {
        UserAchievementProgressDTO request = requestWithProgress(-1);

        assertThrows(BadRequestException.class, () -> service.updateProgress(request));

        verifyNoInteractions(userAchievementRepository, userRepository, achievementRepository);
    }

    @Test
    void rejectsProgressAboveAllowedRangeBeforeAccessingPersistence() {
        UserAchievementProgressDTO request = requestWithProgress(101);

        assertThrows(BadRequestException.class, () -> service.updateProgress(request));

        verifyNoInteractions(userAchievementRepository, userRepository, achievementRepository);
    }

    private UserAchievementProgressDTO requestWithProgress(Integer progress) {
        return UserAchievementProgressDTO.builder()
                .userId(UUID.randomUUID())
                .achId(UUID.randomUUID())
                .progress(progress)
                .build();
    }
}
