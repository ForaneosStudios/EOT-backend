package com.EOT.project.service;

import com.EOT.project.dto.request.UserAchievementProgressDTO;
import com.EOT.project.dto.response.UserAchievementResponseDTO;
import com.EOT.project.exception.BadRequestException;
import com.EOT.project.model.AchievementModel;
import com.EOT.project.model.UserAchievementModel;
import com.EOT.project.model.UserModel;
import com.EOT.project.repository.AchievementRepository;
import com.EOT.project.repository.UserAchievementRepository;
import com.EOT.project.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
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

    @Test
    void unlocksACounterAchievementAtItsConfiguredTarget() {
        UUID userId = UUID.randomUUID();
        UUID achievementId = UUID.randomUUID();
        UserAchievementProgressDTO request = UserAchievementProgressDTO.builder()
                .userId(userId)
                .achId(achievementId)
                .progress(10)
                .build();
        UserModel user = UserModel.builder().userId(userId).username("heredero").build();
        AchievementModel achievement = AchievementModel.builder()
                .achId(achievementId)
                .achName("Susurros absorbidos")
                .achDescription("Absorbe 10 almas.")
                .targetValue(10)
                .progressLabel("almas")
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(achievementRepository.findById(achievementId)).thenReturn(Optional.of(achievement));
        when(userAchievementRepository.findById(any())).thenReturn(Optional.empty());
        when(userAchievementRepository.save(any(UserAchievementModel.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserAchievementResponseDTO result = service.updateProgress(request);

        assertTrue(result.getIsUnlocked());
        assertTrue(result.getProgress() == 10);
    }

    private UserAchievementProgressDTO requestWithProgress(Integer progress) {
        return UserAchievementProgressDTO.builder()
                .userId(UUID.randomUUID())
                .achId(UUID.randomUUID())
                .progress(progress)
                .build();
    }
}
