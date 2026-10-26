package com.EOT.project.service;

import com.EOT.project.dto.request.ThreadRequestDTO;
import com.EOT.project.dto.request.ThreadUpdateRequestDTO;
import com.EOT.project.dto.response.ThreadResponseDTO;
import com.EOT.project.exception.ResourceNotFoundException;
import com.EOT.project.model.CategoryModel;
import com.EOT.project.model.ThreadModel;
import com.EOT.project.model.UserModel;
import com.EOT.project.repository.CategoryRepository;
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
public class ThreadService {

    private final ThreadRepository threadRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<ThreadResponseDTO> findAll() {
        return threadRepository.findByIsDeletedFalseOrderByCreatedAtDesc()
                .stream()
                .map(ThreadResponseDTO::fromModel)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ThreadResponseDTO> findByCategoryId(UUID categoryId) {
        categoryRepository.findById(categoryId)
                .filter(category -> !Boolean.TRUE.equals(category.getIsDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Categoría no encontrada con ID: " + categoryId));
        return threadRepository.findByCategory_CategoryIdAndIsDeletedFalse(categoryId)
                .stream()
                .map(ThreadResponseDTO::fromModel)
                .toList();
    }

    @Transactional(readOnly = true)
    public ThreadResponseDTO findById(UUID id) {
        return ThreadResponseDTO.fromModel(requireActive(id));
    }

    @Transactional
    public ThreadResponseDTO create(ThreadRequestDTO dto) {
        CategoryModel category = categoryRepository.findById(dto.getCategoryId())
                .filter(existing -> !Boolean.TRUE.equals(existing.getIsDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Categoría no encontrada con ID: " + dto.getCategoryId()));

        UserModel user = userRepository.findById(dto.getUserId())
                .filter(existing -> !Boolean.TRUE.equals(existing.getIsDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con ID: " + dto.getUserId()));

        ThreadModel thread = ThreadModel.builder()
                .category(category)
                .user(user)
                .title(dto.getTitle())
                .content(dto.getContent())
                .isLocked(dto.getIsLocked() != null ? dto.getIsLocked() : false)
                .build();

        return ThreadResponseDTO.fromModel(threadRepository.save(thread));
    }

    @Transactional
    public ThreadResponseDTO update(UUID id, ThreadUpdateRequestDTO dto) {
        ThreadModel thread = requireActive(id);
        thread.setTitle(dto.title());
        thread.setContent(dto.content());
        return ThreadResponseDTO.fromModel(threadRepository.save(thread));
    }

    @Transactional
    public ThreadResponseDTO setLocked(UUID id, boolean locked) {
        ThreadModel thread = requireActive(id);
        thread.setIsLocked(locked);
        return ThreadResponseDTO.fromModel(threadRepository.save(thread));
    }

    @Transactional
    public void delete(UUID id) {
        ThreadModel thread = requireActive(id);
        thread.setIsDeleted(true);
        thread.setDeletedAt(Instant.now());
        threadRepository.save(thread);
    }

    private ThreadModel requireActive(UUID id) {
        return threadRepository.findById(id)
                .filter(thread -> !Boolean.TRUE.equals(thread.getIsDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Hilo no encontrado con ID: " + id));
    }
}

