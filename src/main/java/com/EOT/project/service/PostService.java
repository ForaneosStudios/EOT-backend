package com.EOT.project.service;

import com.EOT.project.dto.request.PostRequestDTO;
import com.EOT.project.dto.request.PostUpdateRequestDTO;
import com.EOT.project.dto.response.PostResponseDTO;
import com.EOT.project.exception.BadRequestException;
import com.EOT.project.exception.ConflictException;
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

import java.time.Instant;
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
        threadRepository.findById(threadId)
                .filter(thread -> !Boolean.TRUE.equals(thread.getIsDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Hilo no encontrado con ID: " + threadId));
        return postRepository.findByThread_ThreadIdAndIsDeletedFalseOrderByCreatedAtAsc(threadId)
                .stream()
                .map(PostResponseDTO::fromModel)
                .toList();
    }

    @Transactional(readOnly = true)
    public PostResponseDTO findById(UUID id) {
        return PostResponseDTO.fromModel(requireActive(id));
    }

    @Transactional
    public PostResponseDTO create(PostRequestDTO dto) {
        ThreadModel thread = threadRepository.findById(dto.getThreadId())
                .filter(existing -> !Boolean.TRUE.equals(existing.getIsDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Hilo no encontrado con ID: " + dto.getThreadId()));

        if (Boolean.TRUE.equals(thread.getIsLocked())) {
            throw new ConflictException("El hilo se encuentra bloqueado, no se permiten nuevas publicaciones");
        }

        UserModel user = userRepository.findById(dto.getUserId())
                .filter(existing -> !Boolean.TRUE.equals(existing.getIsDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con ID: " + dto.getUserId()));

        PostModel parentPost = null;
        if (dto.getParentPostId() != null) {
            parentPost = postRepository.findById(dto.getParentPostId())
                    .filter(existing -> !Boolean.TRUE.equals(existing.getIsDeleted()))
                    .orElseThrow(() -> new ResourceNotFoundException("Post padre no encontrado con ID: " + dto.getParentPostId()));
            if (!parentPost.getThread().getThreadId().equals(thread.getThreadId())) {
                throw new BadRequestException("El post padre debe pertenecer al mismo hilo");
            }
        }

        PostModel post = PostModel.builder()
                .thread(thread)
                .user(user)
                .parentPost(parentPost)
                .content(dto.getContent())
                .build();

        return PostResponseDTO.fromModel(postRepository.save(post));
    }

    @Transactional
    public PostResponseDTO update(UUID id, PostUpdateRequestDTO dto, boolean allowLockedThread) {
        PostModel post = requireActive(id);
        if (Boolean.TRUE.equals(post.getThread().getIsLocked()) && !allowLockedThread) {
            throw new ConflictException("El hilo se encuentra bloqueado, no se puede editar la publicación");
        }
        post.setContent(dto.content());
        return PostResponseDTO.fromModel(postRepository.save(post));
    }

    @Transactional
    public void delete(UUID id) {
        PostModel post = requireActive(id);
        post.setIsDeleted(true);
        post.setDeletedAt(Instant.now());
        postRepository.save(post);
    }

    private PostModel requireActive(UUID id) {
        return postRepository.findById(id)
                .filter(post -> !Boolean.TRUE.equals(post.getIsDeleted())
                        && !Boolean.TRUE.equals(post.getThread().getIsDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Post no encontrado con ID: " + id));
    }
}

