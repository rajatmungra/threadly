package com.threadly.notification.dto.response;

import com.threadly.notification.entity.NotificationType;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
    UUID id,
    NotificationType type,
    UUID actorUserId,
    UUID postId,
    UUID commentId,
    boolean read,
    Instant createdAt
) {
}
