package com.threadly.notification.service;

import com.threadly.notification.dto.response.NotificationResponse;
import com.threadly.notification.dto.response.PagedResponse;
import com.threadly.notification.entity.Notification;
import com.threadly.notification.entity.NotificationType;
import com.threadly.notification.event.CommentCreatedEvent;
import com.threadly.notification.exception.InvalidPaginationException;
import com.threadly.notification.exception.NotificationNotFoundException;
import com.threadly.notification.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    void shouldReturnNotificationsWithDeterministicOrderingAndCorrectMetadata() {
        UUID userId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        Instant now = Instant.now();

        Notification notification = new Notification(
            notificationId,
            UUID.randomUUID(),
            userId,
            NotificationType.POST_COMMENT,
            actorUserId,
            postId,
            commentId,
            false,
            now
        );

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        when(notificationRepository.findByUserId(eq(userId), pageableCaptor.capture()))
            .thenReturn(new PageImpl<>(List.of(notification), Pageable.unpaged(), 1));

        PagedResponse<NotificationResponse> result = notificationService.getNotifications(userId, 0, 20);

        Pageable capturedPageable = pageableCaptor.getValue();
        assertThat(capturedPageable.getPageNumber()).isEqualTo(0);
        assertThat(capturedPageable.getPageSize()).isEqualTo(20);
        assertThat(capturedPageable.getSort()).isEqualTo(
            Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        );

        assertThat(result.content()).hasSize(1);
        NotificationResponse response = result.content().get(0);
        assertThat(response.id()).isEqualTo(notificationId);
        assertThat(response.type()).isEqualTo(NotificationType.POST_COMMENT);
        assertThat(response.actorUserId()).isEqualTo(actorUserId);
        assertThat(response.postId()).isEqualTo(postId);
        assertThat(response.commentId()).isEqualTo(commentId);
        assertThat(response.read()).isFalse();
        assertThat(response.createdAt()).isEqualTo(now);
        assertThat(result.totalElements()).isEqualTo(1L);
    }

    @Test
    void shouldReturnEmptyPageWhenUserHasNoNotifications() {
        UUID userId = UUID.randomUUID();

        when(notificationRepository.findByUserId(eq(userId), anyPageable()))
            .thenReturn(new PageImpl<>(Collections.emptyList(), Pageable.unpaged(), 0));

        PagedResponse<NotificationResponse> result = notificationService.getNotifications(userId, 0, 20);

        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isEqualTo(0L);
    }

    @Test
    void shouldThrowWhenPaginationPageIsNegative() {
        UUID userId = UUID.randomUUID();

        assertThatThrownBy(() -> notificationService.getNotifications(userId, -1, 20))
            .isInstanceOf(InvalidPaginationException.class)
            .satisfies(ex -> {
                InvalidPaginationException ipe = (InvalidPaginationException) ex;
                assertThat(ipe.getFieldErrors()).containsKey("page");
            });
    }

    @Test
    void shouldThrowWhenPaginationSizeIsZero() {
        UUID userId = UUID.randomUUID();

        assertThatThrownBy(() -> notificationService.getNotifications(userId, 0, 0))
            .isInstanceOf(InvalidPaginationException.class)
            .satisfies(ex -> {
                InvalidPaginationException ipe = (InvalidPaginationException) ex;
                assertThat(ipe.getFieldErrors()).containsKey("size");
            });
    }

    @Test
    void shouldThrowWhenPaginationSizeExceeds100() {
        UUID userId = UUID.randomUUID();

        assertThatThrownBy(() -> notificationService.getNotifications(userId, 0, 101))
            .isInstanceOf(InvalidPaginationException.class)
            .satisfies(ex -> {
                InvalidPaginationException ipe = (InvalidPaginationException) ex;
                assertThat(ipe.getFieldErrors()).containsKey("size");
            });
    }

    @Test
    void shouldMarkUnreadNotificationAsRead() {
        UUID userId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();

        Notification notification = new Notification(
            notificationId,
            UUID.randomUUID(),
            userId,
            NotificationType.POST_COMMENT,
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            false,
            Instant.now()
        );

        when(notificationRepository.findByIdAndUserId(notificationId, userId))
            .thenReturn(Optional.of(notification));

        notificationService.markAsRead(notificationId, userId);

        assertThat(notification.isRead()).isTrue();
    }

    @Test
    void shouldSucceedIdempotentlyWhenNotificationIsAlreadyRead() {
        UUID userId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();

        Notification notification = new Notification(
            notificationId,
            UUID.randomUUID(),
            userId,
            NotificationType.POST_COMMENT,
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            true,
            Instant.now()
        );

        when(notificationRepository.findByIdAndUserId(notificationId, userId))
            .thenReturn(Optional.of(notification));

        notificationService.markAsRead(notificationId, userId);

        assertThat(notification.isRead()).isTrue();
    }

    @Test
    void shouldThrowWhenNotificationNotFoundOrBelongsToAnotherUser() {
        UUID userId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();

        when(notificationRepository.findByIdAndUserId(notificationId, userId))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markAsRead(notificationId, userId))
            .isInstanceOf(NotificationNotFoundException.class)
            .hasMessageContaining(notificationId.toString());
    }

    @Test
    void shouldSuppressNotificationWhenActorAndRecipientAreSame() {
        UUID userId = UUID.randomUUID();
        CommentCreatedEvent event = new CommentCreatedEvent(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            null,
            userId,
            userId,
            "POST_COMMENT",
            Instant.now()
        );

        notificationService.processCommentCreated(event);

        verify(notificationRepository, never()).saveAndFlush(any());
        verify(notificationRepository, never()).existsBySourceEventId(any());
    }

    private static Pageable anyPageable() {
        return org.mockito.ArgumentMatchers.any(Pageable.class);
    }
}
