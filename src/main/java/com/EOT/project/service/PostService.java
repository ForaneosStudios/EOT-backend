package com.EOT.project.service;

import com.EOT.project.dto.request.PostRequestDTO;
import com.EOT.project.dto.response.PostResponseDTO;
import com.EOT.project.exception.BadRequestException;
import com.EOT.project.exception.ResourceNotFoundException;
import com.EOT.project.model.PostModel;
import com.EOT.project.model.ThreadModel;
import com.EOT.project.model.UserModel;
import com.EOT.project.repository.PostRepository;
import com.EOT.project.repository.ThreadRepository;
import com.EOT.project.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    private final ThreadRepository threadRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<PostResponseDTO> findByThreadId(UUID threadId) {
        return postRepository.findByThread_ThreadIdAndIsDeletedFalseOrderByCreatedAtAsc(threadId)
                .stream()
                .map(PostResponseDTO::fromModel)
                .toList();
    }

    @Transactional
    public PostResponseDTO create(PostRequestDTO dto) {
        ThreadModel thread = threadRepository.findById(dto.getThreadId())
                .orElseThrow(() -> new ResourceNotFoundException("Hilo no encontrado con ID: " + dto.getThreadId()));

        if (Boolean.TRUE.equals(thread.getIsLocked())) {
            throw new BadRequestException("El hilo se encuentra bloqueado, no se permiten nuevas publicaciones");
        }

        UserModel user = userRepository.findById(dto.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con ID: " + dto.getUserId()));

        PostModel parentPost = null;
        if (dto.getParentPostId() != null) {
            parentPost = postRepository.findById(dto.getParentPostId())
                    .orElseThrow(() -> new ResourceNotFoundException("Post padre no encontrado con ID: " + dto.getParentPostId()));
        }

        PostModel post = PostModel.builder()
                .thread(thread)
                .user(user)
                .parentPost(parentPost)
                .content(dto.getContent())
                .build();

        return PostResponseDTO.fromModel(postRepository.save(post));
    }
}

