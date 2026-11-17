package com.EOT.project.service;

import com.EOT.project.dto.request.ThreadRequestDTO;
import com.EOT.project.model.*;
import com.EOT.project.repository.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ThreadServiceTest {
    @Mock ThreadRepository threads; @Mock CategoryRepository categories; @Mock UserRepository users; @InjectMocks ThreadService service;
    @Test void createsTrimmedThreadInCommunityWhenCategoryIsMissing() {
        UUID id = UUID.randomUUID(); when(users.findById(id)).thenReturn(Optional.of(UserModel.builder().userId(id).username("u").build()));
        when(categories.findByCategoryName("Comunidad")).thenReturn(Optional.of(CategoryModel.builder().categoryId(UUID.randomUUID()).categoryName("Comunidad").build()));
        when(threads.save(any(ThreadModel.class))).thenAnswer(i -> i.getArgument(0));
        service.create(ThreadRequestDTO.builder().userId(id).title("  Titulo  ").content("  Contenido  ").build());
        ArgumentCaptor<ThreadModel> saved = ArgumentCaptor.forClass(ThreadModel.class); verify(threads).save(saved.capture());
        assertThat(saved.getValue().getTitle()).isEqualTo("Titulo"); assertThat(saved.getValue().getContent()).isEqualTo("Contenido");
    }
}
