package com.threadly.post.event;

import java.time.Instant;
import java.util.UUID;

public record CommentCreatedEvent(
    UUID eventId,
    UUID commentId,
    UUID postId,
    UUID parentCommentId,
    UUID actorUserId,
    UUID recipientUserId,
    String type,
    Instant createdAt
) {
}
