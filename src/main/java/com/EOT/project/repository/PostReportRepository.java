package com.EOT.project.repository;

import com.EOT.project.model.PostReportModel;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostReportRepository extends JpaRepository<PostReportModel, UUID> {
    Page<PostReportModel> findByPost_Thread_ThreadIdAndIsDeletedFalseOrderByCreatedAtDesc(UUID threadId, Pageable pageable);
}
