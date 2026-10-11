package com.EOT.project.repository;

import com.EOT.project.model.PostModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PostRepository extends JpaRepository<PostModel, UUID> {

    List<PostModel> findByThread_ThreadIdAndIsDeletedFalseOrderByCreatedAtAsc(UUID threadId);

    List<PostModel> findByUser_UserIdAndIsDeletedFalse(UUID userId);

    List<PostModel> findByParentPost_PostIdAndIsDeletedFalse(UUID parentPostId);
}

