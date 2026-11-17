package com.EOT.project.service;

import com.EOT.project.dto.request.PostReportRequestDTO;
import com.EOT.project.exception.BadRequestException;
import com.EOT.project.model.*;
import com.EOT.project.repository.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostReportServiceTest {
    @Mock PostReportRepository reports; @Mock PostRepository posts; @Mock ThreadRepository threads; @InjectMocks PostReportService service;
    @Test void rejectsReportingOwnPost() {
        UUID userId = UUID.randomUUID(), postId = UUID.randomUUID(); UserModel user = UserModel.builder().userId(userId).username("u").build();
        when(posts.findById(postId)).thenReturn(Optional.of(PostModel.builder().postId(postId).user(user).thread(ThreadModel.builder().build()).content("x").build()));
        assertThrows(BadRequestException.class, () -> service.create(postId, user, new PostReportRequestDTO("spam")));
    }
}
