package com.EOT.project.dto.response;

import com.EOT.project.model.PostModel;
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
public class PostResponseDTO {

    private UUID postId;
    private UUID threadId;
    private UUID userId;
    private String username;
    private String userProfilePicture;
    private UUID parentPostId;
    private String content;
    private Instant createdAt;
    private Instant updatedAt;

    public static PostResponseDTO fromModel(PostModel post) {
        if (post == null) {
            return null;
        }
        return PostResponseDTO.builder()
                .postId(post.getPostId())
                .threadId(post.getThread() != null ? post.getThread().getThreadId() : null)
                .userId(post.getUser() != null ? post.getUser().getUserId() : null)
                .username(post.getUser() != null ? post.getUser().getUsername() : null)
                .userProfilePicture(post.getUser() != null ? post.getUser().getProfilePicture() : null)
                .parentPostId(post.getParentPost() != null ? post.getParentPost().getPostId() : null)
                .content(post.getContent())
                .createdAt(post.getCreatedAt())
                .updatedAt(post.getUpdatedAt())
                .build();
    }
}

