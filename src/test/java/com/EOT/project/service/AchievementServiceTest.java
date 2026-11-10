package com.EOT.project.service;

import com.EOT.project.dto.request.AchievementRequestDTO;
import com.EOT.project.exception.ConflictException;
import com.EOT.project.model.AchievementModel;
import com.EOT.project.repository.AchievementRepository;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AchievementServiceTest {
    @Mock AchievementRepository repository; @InjectMocks AchievementService service;
    @Test void createsUniqueAchievementAndSoftDeletesIt() {
        AchievementRequestDTO dto = AchievementRequestDTO.builder().achName("Explorador").achDescription("Explora").build();
        when(repository.findByAchName(dto.getAchName())).thenReturn(Optional.empty());
        when(repository.save(any(AchievementModel.class))).thenAnswer(i -> i.getArgument(0));
        assertThat(service.create(dto).getAchName()).isEqualTo("Explorador");
        AchievementModel duplicate = AchievementModel.builder().achId(UUID.randomUUID()).achName("Explorador").build();
        when(repository.findByAchName("Explorador")).thenReturn(Optional.of(duplicate));
        assertThrows(ConflictException.class, () -> service.create(dto));
        when(repository.findById(duplicate.getAchId())).thenReturn(Optional.of(duplicate)); service.delete(duplicate.getAchId());
        assertThat(duplicate.getIsDeleted()).isTrue(); verify(repository).save(duplicate);
    }
}
