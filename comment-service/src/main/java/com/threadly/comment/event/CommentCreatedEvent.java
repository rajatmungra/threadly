package com.threadly.comment.event;

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
    public static final String TYPE_POST_COMMENT = "POST_COMMENT";
    public static final String TYPE_COMMENT_REPLY = "COMMENT_REPLY";
}
