package com.EOT.project.repository;

import com.EOT.project.model.PostModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Repository
public interface PostRepository extends JpaRepository<PostModel, UUID> {

    List<PostModel> findByThread_ThreadIdAndIsDeletedFalseOrderByCreatedAtAsc(UUID threadId);

    List<PostModel> findByUser_UserIdAndIsDeletedFalse(UUID userId);

    List<PostModel> findByParentPost_PostIdAndIsDeletedFalse(UUID parentPostId);

    Page<PostModel> findByThread_ThreadIdAndParentPostIsNullAndIsDeletedFalseOrderByCreatedAtAsc(UUID threadId, Pageable pageable);
    Page<PostModel> findByThread_ThreadIdAndParentPost_PostIdAndIsDeletedFalseOrderByCreatedAtAsc(UUID threadId, UUID parentPostId, Pageable pageable);

    /** Loads the row with a write lock so concurrent edits are applied one after another. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from PostModel e where e.postId = :id")
    Optional<PostModel> findByIdForUpdate(@Param("id") UUID id);
}
