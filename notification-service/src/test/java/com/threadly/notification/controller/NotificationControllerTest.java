package com.threadly.notification.controller;

import com.threadly.notification.config.SecurityConfig;
import com.threadly.notification.dto.response.NotificationResponse;
import com.threadly.notification.dto.response.PagedResponse;
import com.threadly.notification.entity.NotificationType;
import com.threadly.notification.exception.CustomAuthenticationEntryPoint;
import com.threadly.notification.exception.GlobalExceptionHandler;
import com.threadly.notification.exception.InvalidPaginationException;
import com.threadly.notification.exception.NotificationNotFoundException;
import com.threadly.notification.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = NotificationController.class)
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, GlobalExceptionHandler.class})
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;

    @Test
    void shouldGetNotificationsWhenAuthenticated() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        Instant now = Instant.now();

        NotificationResponse response = new NotificationResponse(
            notificationId,
            NotificationType.POST_COMMENT,
            actorUserId,
            postId,
            commentId,
            false,
            now
        );

        PagedResponse<NotificationResponse> pagedResponse = new PagedResponse<>(
            List.of(response),
            0,
            20,
            1L,
            1,
            true
        );

        when(notificationService.getNotifications(eq(userId), eq(0), eq(20)))
            .thenReturn(pagedResponse);

        mockMvc.perform(get("/api/v1/notifications")
                .with(jwt().jwt(jwt -> jwt.subject(userId.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].id").value(notificationId.toString()))
            .andExpect(jsonPath("$.content[0].type").value("POST_COMMENT"))
            .andExpect(jsonPath("$.content[0].actorUserId").value(actorUserId.toString()))
            .andExpect(jsonPath("$.content[0].postId").value(postId.toString()))
            .andExpect(jsonPath("$.content[0].commentId").value(commentId.toString()))
            .andExpect(jsonPath("$.content[0].read").value(false))
            .andExpect(jsonPath("$.content[0].createdAt").exists())
            .andExpect(jsonPath("$.content[0].sourceEventId").doesNotExist())
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(20))
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.totalPages").value(1))
            .andExpect(jsonPath("$.last").value(true));

        verify(notificationService).getNotifications(eq(userId), eq(0), eq(20));
    }

    @Test
    void shouldReturnEmptyPageWhenUserHasNoNotifications() throws Exception {
        UUID userId = UUID.randomUUID();

        PagedResponse<NotificationResponse> pagedResponse = new PagedResponse<>(
            Collections.emptyList(),
            0,
            20,
            0L,
            0,
            true
        );

        when(notificationService.getNotifications(eq(userId), eq(0), eq(20)))
            .thenReturn(pagedResponse);

        mockMvc.perform(get("/api/v1/notifications")
                .with(jwt().jwt(jwt -> jwt.subject(userId.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(0))
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(20))
            .andExpect(jsonPath("$.totalElements").value(0))
            .andExpect(jsonPath("$.totalPages").value(0))
            .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    void shouldReturn401WhenGetNotificationsWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/notifications"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
            .andExpect(jsonPath("$.message").value("Authentication required"));
    }

    @Test
    void shouldReturn401WhenGetNotificationsWithMalformedJwtSubject() throws Exception {
        mockMvc.perform(get("/api/v1/notifications")
                .with(jwt().jwt(jwt -> jwt.subject("not-a-valid-uuid"))))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
            .andExpect(jsonPath("$.message").value("Authentication required"));
    }

    @Test
    void shouldReturn400WhenGetNotificationsWithNegativePage() throws Exception {
        UUID userId = UUID.randomUUID();

        when(notificationService.getNotifications(eq(userId), eq(-1), eq(20)))
            .thenThrow(new InvalidPaginationException("Invalid pagination parameters", Map.of("page", "Page index must not be less than zero")));

        mockMvc.perform(get("/api/v1/notifications")
                .param("page", "-1")
                .with(jwt().jwt(jwt -> jwt.subject(userId.toString()))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.errors.page").value("Page index must not be less than zero"));
    }

    @Test
    void shouldReturn400WhenGetNotificationsWithSizeZero() throws Exception {
        UUID userId = UUID.randomUUID();

        when(notificationService.getNotifications(eq(userId), eq(0), eq(0)))
            .thenThrow(new InvalidPaginationException("Invalid pagination parameters", Map.of("size", "Page size must be at least 1")));

        mockMvc.perform(get("/api/v1/notifications")
                .param("size", "0")
                .with(jwt().jwt(jwt -> jwt.subject(userId.toString()))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.errors.size").value("Page size must be at least 1"));
    }

    @Test
    void shouldReturn400WhenGetNotificationsWithSizeGreaterThan100() throws Exception {
        UUID userId = UUID.randomUUID();

        when(notificationService.getNotifications(eq(userId), eq(0), eq(101)))
            .thenThrow(new InvalidPaginationException("Invalid pagination parameters", Map.of("size", "Page size must not exceed 100")));

        mockMvc.perform(get("/api/v1/notifications")
                .param("size", "101")
                .with(jwt().jwt(jwt -> jwt.subject(userId.toString()))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.errors.size").value("Page size must not exceed 100"));
    }

    @Test
    void shouldMarkNotificationAsReadWhenAuthenticatedAsOwner() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();

        doNothing().when(notificationService).markAsRead(eq(notificationId), eq(userId));

        mockMvc.perform(patch("/api/v1/notifications/{notificationId}/read", notificationId)
                .with(jwt().jwt(jwt -> jwt.subject(userId.toString()))))
            .andExpect(status().isNoContent());

        verify(notificationService).markAsRead(eq(notificationId), eq(userId));
    }

    @Test
    void shouldReturn204WhenMarkingAlreadyReadNotification() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();

        doNothing().when(notificationService).markAsRead(eq(notificationId), eq(userId));

        mockMvc.perform(patch("/api/v1/notifications/{notificationId}/read", notificationId)
                .with(jwt().jwt(jwt -> jwt.subject(userId.toString()))))
            .andExpect(status().isNoContent());

        verify(notificationService).markAsRead(eq(notificationId), eq(userId));
    }

    @Test
    void shouldReturn404WhenNotificationNotFoundOrBelongsToAnotherUser() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();

        doThrow(new NotificationNotFoundException("Notification not found: " + notificationId))
            .when(notificationService).markAsRead(eq(notificationId), eq(userId));

        mockMvc.perform(patch("/api/v1/notifications/{notificationId}/read", notificationId)
                .with(jwt().jwt(jwt -> jwt.subject(userId.toString()))))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"))
            .andExpect(jsonPath("$.message").value("Notification not found: " + notificationId));
    }

    @Test
    void shouldReturn400WhenNotificationIdIsMalformed() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(patch("/api/v1/notifications/{notificationId}/read", "not-a-valid-uuid")
                .with(jwt().jwt(jwt -> jwt.subject(userId.toString()))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.errors.notificationId").value("Invalid value for parameter 'notificationId'"));
    }

    @Test
    void shouldReturn401WhenMarkAsReadWithoutToken() throws Exception {
        UUID notificationId = UUID.randomUUID();

        mockMvc.perform(patch("/api/v1/notifications/{notificationId}/read", notificationId))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
            .andExpect(jsonPath("$.message").value("Authentication required"));
    }

    @Test
    void shouldReturn401WhenMarkAsReadWithMalformedJwtSubject() throws Exception {
        UUID notificationId = UUID.randomUUID();

        mockMvc.perform(patch("/api/v1/notifications/{notificationId}/read", notificationId)
                .with(jwt().jwt(jwt -> jwt.subject("invalid-uuid"))))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
            .andExpect(jsonPath("$.message").value("Authentication required"));
    }
}
