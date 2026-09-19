package com.threadly.comment.dto.response;

import com.threadly.comment.entity.Comment;

import java.time.Instant;
import java.util.UUID;

public record CommentResponse(
    UUID id,
    UUID postId,
    UUID authorId,
    UUID parentCommentId,
    String content,
    int score,
    Instant createdAt,
    Instant updatedAt
) {
    public static CommentResponse fromEntity(Comment comment) {
        return new CommentResponse(
            comment.getId(),
            comment.getPostId(),
            comment.getAuthorId(),
            comment.getParentCommentId(),
            comment.getContent(),
            comment.getScore(),
            comment.getCreatedAt(),
            comment.getUpdatedAt()
        );
    }
}
