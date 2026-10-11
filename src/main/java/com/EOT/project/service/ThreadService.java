package com.EOT.project.service;

import com.EOT.project.dto.request.ThreadRequestDTO;
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
        return threadRepository.findByCategory_CategoryIdAndIsDeletedFalse(categoryId)
                .stream()
                .map(ThreadResponseDTO::fromModel)
                .toList();
    }

    @Transactional(readOnly = true)
    public ThreadResponseDTO findById(UUID id) {
        ThreadModel thread = threadRepository.findById(id)
                .filter(t -> !t.getIsDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Hilo no encontrado con ID: " + id));
        return ThreadResponseDTO.fromModel(thread);
    }

    @Transactional
    public ThreadResponseDTO create(ThreadRequestDTO dto) {
        CategoryModel category = categoryRepository.findById(dto.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Categoría no encontrada con ID: " + dto.getCategoryId()));

        UserModel user = userRepository.findById(dto.getUserId())
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
}

