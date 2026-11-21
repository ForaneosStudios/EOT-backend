package com.EOT.project.repository;

import com.EOT.project.model.ThreadModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface ThreadRepository extends JpaRepository<ThreadModel, UUID> {

    List<ThreadModel> findByCategory_CategoryIdAndIsDeletedFalse(UUID categoryId);

    List<ThreadModel> findByUser_UserIdAndIsDeletedFalse(UUID userId);

    List<ThreadModel> findByIsDeletedFalseOrderByCreatedAtDesc();

    boolean existsByCategory_CategoryIdAndIsDeletedFalse(UUID categoryId);

    @Query("""
      select t from ThreadModel t where t.isDeleted = false
      and (:title = '' or lower(t.title) like lower(concat('%', :title, '%')))
      and (:author = '' or lower(t.user.username) like lower(concat('%', :author, '%')))
      and (:participantId is null or t.user.userId = :participantId or exists
        (select p from PostModel p where p.thread = t and p.user.userId = :participantId and p.isDeleted = false))
      """)
    Page<ThreadModel> search(@Param("title") String title, @Param("author") String author,
                             @Param("participantId") UUID participantId, Pageable pageable);

    /** Loads the row with a write lock so concurrent edits are applied one after another. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from ThreadModel e where e.threadId = :id")
    Optional<ThreadModel> findByIdForUpdate(@Param("id") UUID id);
}
