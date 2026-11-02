package com.EOT.project.service;

import com.EOT.project.dto.request.PostReportRequestDTO;
import com.EOT.project.dto.response.PostReportResponseDTO;
import com.EOT.project.exception.*;
import com.EOT.project.model.*;
import com.EOT.project.repository.*;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class PostReportService {
    private final PostReportRepository reports; private final PostRepository posts; private final ThreadRepository threads;
    @Transactional public PostReportResponseDTO create(UUID postId, UserModel reporter, PostReportRequestDTO dto) {
        PostModel post = posts.findById(postId).filter(p -> !Boolean.TRUE.equals(p.getIsDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Publicación no encontrada"));
        if (post.getUser().getUserId().equals(reporter.getUserId())) throw new BadRequestException("No puedes reportar tu propia publicación");
        return PostReportResponseDTO.fromModel(reports.save(PostReportModel.builder().post(post).reporter(reporter).reason(dto.reason().trim()).build()));
    }
    @Transactional(readOnly = true) public Page<PostReportResponseDTO> findForThread(UUID threadId, UUID requesterId, Pageable pageable) {
        ThreadModel thread = threads.findById(threadId).filter(t -> !Boolean.TRUE.equals(t.getIsDeleted())).orElseThrow(() -> new ResourceNotFoundException("Hilo no encontrado"));
        if (!thread.getUser().getUserId().equals(requesterId)) throw new BadRequestException("Solo el autor del hilo puede revisar reportes");
        return reports.findByPost_Thread_ThreadIdAndIsDeletedFalseOrderByCreatedAtDesc(threadId, pageable).map(PostReportResponseDTO::fromModel);
    }
    @Transactional public PostReportResponseDTO setStatus(UUID reportId, UUID requesterId, PostReportStatus status) {
        PostReportModel report = reports.findById(reportId).filter(r -> !Boolean.TRUE.equals(r.getIsDeleted())).orElseThrow(() -> new ResourceNotFoundException("Reporte no encontrado"));
        if (!report.getPost().getThread().getUser().getUserId().equals(requesterId)) throw new BadRequestException("Solo el autor del hilo puede revisar reportes");
        report.setStatus(status); report.setReviewedAt(Instant.now()); return PostReportResponseDTO.fromModel(reports.save(report));
    }
}
