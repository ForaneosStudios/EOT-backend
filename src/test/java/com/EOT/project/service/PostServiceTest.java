package com.EOT.project.service;

import com.EOT.project.dto.request.PostRequestDTO;
import com.EOT.project.exception.ConflictException;
import com.EOT.project.model.*;
import com.EOT.project.repository.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {
    @Mock PostRepository posts; @Mock ThreadRepository threads; @Mock UserRepository users; @InjectMocks PostService service;
    @Test void rejectsNewPostInLockedThread() {
        UUID threadId = UUID.randomUUID(), userId = UUID.randomUUID();
        ThreadModel locked = ThreadModel.builder().threadId(threadId).isLocked(true).build(); when(threads.findById(threadId)).thenReturn(Optional.of(locked));
        assertThrows(ConflictException.class, () -> service.create(PostRequestDTO.builder().threadId(threadId).userId(userId).content("Mensaje").build()));
        verifyNoInteractions(users, posts);
    }
}
