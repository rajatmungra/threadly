package com.threadly.notification;

import com.threadly.notification.entity.Notification;
import com.threadly.notification.entity.NotificationType;
import com.threadly.notification.event.CommentCreatedEvent;
import com.threadly.notification.exception.NotificationNotFoundException;
import com.threadly.notification.repository.NotificationRepository;
import com.threadly.notification.service.NotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class NotificationIntegrationTest {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAllInBatch();
    }

    @AfterEach
    void tearDown() {
        notificationRepository.deleteAllInBatch();
    }

    @Test
    void shouldPersistNotificationInDatabase() {
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-09-19T10:00:00Z");

        CommentCreatedEvent event = new CommentCreatedEvent(
            eventId,
            commentId,
            postId,
            null,
            actorUserId,
            userId,
            "POST_COMMENT",
            createdAt
        );

        notificationService.processCommentCreated(event);

        Optional<Notification> found = notificationRepository.findBySourceEventId(eventId);
        assertThat(found).isPresent();
        Notification notification = found.get();
        assertThat(notification.getUserId()).isEqualTo(userId);
        assertThat(notification.getActorUserId()).isEqualTo(actorUserId);
        assertThat(notification.getPostId()).isEqualTo(postId);
        assertThat(notification.getCommentId()).isEqualTo(commentId);
        assertThat(notification.getType()).isEqualTo(NotificationType.POST_COMMENT);
        assertThat(notification.isRead()).isFalse();
        assertThat(notification.getCreatedAt()).isEqualTo(createdAt);
    }

    @Test
    void shouldEnforceUniqueSourceEventIdConstraint() {
        UUID eventId = UUID.randomUUID();
        Instant now = Instant.now();

        Notification notification1 = new Notification(
            UUID.randomUUID(),
            eventId,
            UUID.randomUUID(),
            NotificationType.POST_COMMENT,
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            false,
            now
        );
        notificationRepository.saveAndFlush(notification1);

        Notification notification2 = new Notification(
            UUID.randomUUID(),
            eventId,
            UUID.randomUUID(),
            NotificationType.COMMENT_REPLY,
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            false,
            now
        );

        assertThatThrownBy(() -> notificationRepository.saveAndFlush(notification2))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldDeduplicateIdenticalEventIdInNotificationService() {
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        CommentCreatedEvent event = new CommentCreatedEvent(
            eventId,
            UUID.randomUUID(),
            UUID.randomUUID(),
            null,
            UUID.randomUUID(),
            userId,
            "POST_COMMENT",
            Instant.now()
        );

        // First delivery
        notificationService.processCommentCreated(event);
        assertThat(notificationRepository.count()).isEqualTo(1L);

        // Duplicate delivery
        notificationService.processCommentCreated(event);
        assertThat(notificationRepository.count()).isEqualTo(1L);
    }

    @Test
    void shouldEnforceUserIsolation() {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        Instant now = Instant.now();

        Notification notifA1 = new Notification(
            UUID.randomUUID(), UUID.randomUUID(), userA, NotificationType.POST_COMMENT,
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), false, now.minusSeconds(2)
        );
        Notification notifA2 = new Notification(
            UUID.randomUUID(), UUID.randomUUID(), userA, NotificationType.COMMENT_REPLY,
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), false, now.minusSeconds(1)
        );
        Notification notifB1 = new Notification(
            UUID.randomUUID(), UUID.randomUUID(), userB, NotificationType.POST_COMMENT,
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), false, now
        );

        notificationRepository.saveAllAndFlush(java.util.List.of(notifA1, notifA2, notifB1));

        var responseA = notificationService.getNotifications(userA, 0, 20);
        assertThat(responseA.content()).hasSize(2);
        assertThat(responseA.content()).allMatch(n -> !n.id().equals(notifB1.getId()));

        var responseB = notificationService.getNotifications(userB, 0, 20);
        assertThat(responseB.content()).hasSize(1);
        assertThat(responseB.content().get(0).id()).isEqualTo(notifB1.getId());
    }

    @Test
    void shouldOrderNotificationsDeterministicallyNewestFirstAndIdDesc() {
        UUID userId = UUID.randomUUID();
        Instant older = Instant.parse("2026-09-19T10:00:00Z");
        Instant newer = Instant.parse("2026-09-19T11:00:00Z");

        UUID idLow = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID idHigh = UUID.fromString("00000000-0000-0000-0000-000000000002");

        Notification notifOlder = new Notification(
            UUID.randomUUID(), UUID.randomUUID(), userId, NotificationType.POST_COMMENT,
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), false, older
        );
        Notification notifNewerLowId = new Notification(
            idLow, UUID.randomUUID(), userId, NotificationType.POST_COMMENT,
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), false, newer
        );
        Notification notifNewerHighId = new Notification(
            idHigh, UUID.randomUUID(), userId, NotificationType.COMMENT_REPLY,
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), false, newer
        );

        notificationRepository.saveAllAndFlush(java.util.List.of(notifOlder, notifNewerLowId, notifNewerHighId));

        var response = notificationService.getNotifications(userId, 0, 20);
        assertThat(response.content()).hasSize(3);
        // Tie-breaker for equal timestamp 'newer': idHigh first, then idLow
        assertThat(response.content().get(0).id()).isEqualTo(idHigh);
        assertThat(response.content().get(1).id()).isEqualTo(idLow);
        // Older timestamp last
        assertThat(response.content().get(2).id()).isEqualTo(notifOlder.getId());
    }

    @Test
    void shouldPaginateNotificationsCorrectly() {
        UUID userId = UUID.randomUUID();
        Instant baseTime = Instant.parse("2026-09-19T10:00:00Z");

        for (int i = 0; i < 5; i++) {
            Notification n = new Notification(
                UUID.randomUUID(), UUID.randomUUID(), userId, NotificationType.POST_COMMENT,
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), false, baseTime.plusSeconds(i)
            );
            notificationRepository.save(n);
        }
        notificationRepository.flush();

        var page0 = notificationService.getNotifications(userId, 0, 2);
        assertThat(page0.content()).hasSize(2);
        assertThat(page0.page()).isEqualTo(0);
        assertThat(page0.size()).isEqualTo(2);
        assertThat(page0.totalElements()).isEqualTo(5L);
        assertThat(page0.totalPages()).isEqualTo(3);
        assertThat(page0.last()).isFalse();

        var page2 = notificationService.getNotifications(userId, 2, 2);
        assertThat(page2.content()).hasSize(1);
        assertThat(page2.page()).isEqualTo(2);
        assertThat(page2.size()).isEqualTo(2);
        assertThat(page2.totalElements()).isEqualTo(5L);
        assertThat(page2.totalPages()).isEqualTo(3);
        assertThat(page2.last()).isTrue();
    }

    @Test
    void shouldPersistMarkAsReadInDatabase() {
        UUID userId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();

        Notification notification = new Notification(
            notificationId, UUID.randomUUID(), userId, NotificationType.POST_COMMENT,
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), false, Instant.now()
        );
        notificationRepository.saveAndFlush(notification);

        notificationService.markAsRead(notificationId, userId);

        Notification updated = notificationRepository.findById(notificationId).orElseThrow();
        assertThat(updated.isRead()).isTrue();
    }

    @Test
    void shouldProtectOwnershipWhenMarkingAsRead() {
        UUID ownerId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();

        Notification notification = new Notification(
            notificationId, UUID.randomUUID(), ownerId, NotificationType.POST_COMMENT,
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), false, Instant.now()
        );
        notificationRepository.saveAndFlush(notification);

        assertThatThrownBy(() -> notificationService.markAsRead(notificationId, otherUserId))
            .isInstanceOf(NotificationNotFoundException.class);

        Notification unchanged = notificationRepository.findById(notificationId).orElseThrow();
        assertThat(unchanged.isRead()).isFalse();
    }
}
