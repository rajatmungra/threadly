package com.threadly.comment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateCommentRequest(
    @NotBlank(message = "Content is required")
    @Size(max = 10000, message = "Content must not exceed 10000 characters")
    String content,

    UUID parentCommentId
) {
    public CreateCommentRequest {
        content = content != null ? content.trim() : null;
    }
}
