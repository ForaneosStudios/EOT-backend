package com.EOT.project.dto.response;

import com.EOT.project.model.ThreadModel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThreadResponseDTO {

    private UUID threadId;
    private UUID categoryId;
    private String categoryName;
    private UUID userId;
    private String username;
    private String title;
    private String content;
    private Boolean isLocked;
    private Instant createdAt;
    private Instant updatedAt;

    public static ThreadResponseDTO fromModel(ThreadModel thread) {
        if (thread == null) {
            return null;
        }
        return ThreadResponseDTO.builder()
                .threadId(thread.getThreadId())
                .categoryId(thread.getCategory() != null ? thread.getCategory().getCategoryId() : null)
                .categoryName(thread.getCategory() != null ? thread.getCategory().getCategoryName() : null)
                .userId(thread.getUser() != null ? thread.getUser().getUserId() : null)
                .username(thread.getUser() != null ? thread.getUser().getUsername() : null)
                .title(thread.getTitle())
                .content(thread.getContent())
                .isLocked(thread.getIsLocked())
                .createdAt(thread.getCreatedAt())
                .updatedAt(thread.getUpdatedAt())
                .build();
    }
}

