package com.EOT.project.repository;

import com.EOT.project.model.ThreadModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ThreadRepository extends JpaRepository<ThreadModel, UUID> {

    List<ThreadModel> findByCategory_CategoryIdAndIsDeletedFalse(UUID categoryId);

    List<ThreadModel> findByUser_UserIdAndIsDeletedFalse(UUID userId);

    List<ThreadModel> findByIsDeletedFalseOrderByCreatedAtDesc();

    boolean existsByCategory_CategoryIdAndIsDeletedFalse(UUID categoryId);
}

