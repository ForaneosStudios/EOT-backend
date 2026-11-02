package com.EOT.project.controller;

import com.EOT.project.dto.request.*;
import com.EOT.project.dto.response.PostReportResponseDTO;
import com.EOT.project.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController @RequestMapping("/api/v1") @RequiredArgsConstructor
public class PostReportController {
    private final PostReportService reports; private final CurrentUserService currentUser;
    @PostMapping("/posts/{postId}/reports") public PostReportResponseDTO report(@PathVariable UUID postId, @Valid @RequestBody PostReportRequestDTO dto, Authentication auth) {
        return reports.create(postId, currentUser.requireProfile(auth), dto);
    }
    @GetMapping("/threads/{threadId}/reports") public Page<PostReportResponseDTO> list(@PathVariable UUID threadId, Authentication auth, @RequestParam(defaultValue = "0") int page) {
        return reports.findForThread(threadId, currentUser.requireProfile(auth).getUserId(), PageRequest.of(Math.max(0, page), 10));
    }
    @PatchMapping("/post-reports/{reportId}") public PostReportResponseDTO review(@PathVariable UUID reportId, @Valid @RequestBody PostReportStatusRequestDTO dto, Authentication auth) {
        return reports.setStatus(reportId, currentUser.requireProfile(auth).getUserId(), dto.status());
    }
}
