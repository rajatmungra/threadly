package com.threadly.post.dto.response;

import com.threadly.post.entity.Post;
import com.threadly.post.entity.PostType;

import java.time.Instant;
import java.util.UUID;

public record PostResponse(
    UUID id,
    UUID communityId,
    UUID authorId,
    String title,
    String content,
    String url,
    PostType type,
    int score,
    int commentCount,
    Instant createdAt,
    Instant updatedAt
) {
    public static PostResponse fromEntity(Post post) {
        return new PostResponse(
            post.getId(),
            post.getCommunityId(),
            post.getAuthorId(),
            post.getTitle(),
            post.getContent(),
            post.getUrl(),
            post.getType(),
            post.getScore(),
            post.getCommentCount(),
            post.getCreatedAt(),
            post.getUpdatedAt()
        );
    }
}
