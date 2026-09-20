package com.threadly.notification.controller;

import com.threadly.notification.dto.response.NotificationResponse;
import com.threadly.notification.dto.response.PagedResponse;
import com.threadly.notification.service.NotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public PagedResponse<NotificationResponse> getNotifications(
        @AuthenticationPrincipal Jwt jwt,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        UUID userId = parseUserId(jwt);
        return notificationService.getNotifications(userId, page, size);
    }

    @PatchMapping("/{notificationId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAsRead(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID notificationId
    ) {
        UUID userId = parseUserId(jwt);
        notificationService.markAsRead(notificationId, userId);
    }

    private UUID parseUserId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new InvalidBearerTokenException("Missing token subject");
        }
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException ex) {
            throw new InvalidBearerTokenException("Invalid token subject format");
        }
    }
}
