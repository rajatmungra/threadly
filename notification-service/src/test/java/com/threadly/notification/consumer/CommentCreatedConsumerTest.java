package com.threadly.notification.consumer;

import com.threadly.notification.entity.Notification;
import com.threadly.notification.entity.NotificationType;
import com.threadly.notification.event.CommentCreatedEvent;
import com.threadly.notification.repository.NotificationRepository;
import com.threadly.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentCreatedConsumerTest {

    @Mock
    private NotificationRepository notificationRepository;

    private ObjectMapper objectMapper;
    private NotificationService notificationService;
    private CommentCreatedConsumer consumer;

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder().build();
        notificationService = new NotificationService(notificationRepository);
        consumer = new CommentCreatedConsumer(notificationService, objectMapper);
    }

    @Test
    void shouldPersistNotificationForValidPostCommentEvent() {
        UUID eventId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();
        UUID recipientUserId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-09-19T10:00:00Z");

        CommentCreatedEvent event = new CommentCreatedEvent(
            eventId,
            commentId,
            postId,
            null,
            actorUserId,
            recipientUserId,
            "POST_COMMENT",
            createdAt
        );

        when(notificationRepository.existsBySourceEventId(eventId)).thenReturn(false);

        consumer.consume(asJson(event));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).saveAndFlush(captor.capture());

        Notification saved = captor.getValue();
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getSourceEventId()).isEqualTo(eventId);
        assertThat(saved.getUserId()).isEqualTo(recipientUserId);
        assertThat(saved.getType()).isEqualTo(NotificationType.POST_COMMENT);
        assertThat(saved.getActorUserId()).isEqualTo(actorUserId);
        assertThat(saved.getPostId()).isEqualTo(postId);
        assertThat(saved.getCommentId()).isEqualTo(commentId);
        assertThat(saved.isRead()).isFalse();
        assertThat(saved.getCreatedAt()).isEqualTo(createdAt);
    }

    @Test
    void shouldPersistNotificationForValidCommentReplyEvent() {
        UUID eventId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        UUID parentCommentId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();
        UUID recipientUserId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-09-19T11:00:00Z");

        CommentCreatedEvent event = new CommentCreatedEvent(
            eventId,
            commentId,
            postId,
            parentCommentId,
            actorUserId,
            recipientUserId,
            "COMMENT_REPLY",
            createdAt
        );

        when(notificationRepository.existsBySourceEventId(eventId)).thenReturn(false);

        consumer.consume(asJson(event));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).saveAndFlush(captor.capture());

        Notification saved = captor.getValue();
        assertThat(saved.getType()).isEqualTo(NotificationType.COMMENT_REPLY);
        assertThat(saved.getUserId()).isEqualTo(recipientUserId);
        assertThat(saved.getActorUserId()).isEqualTo(actorUserId);
        assertThat(saved.isRead()).isFalse();
    }

    @Test
    void shouldIgnoreDuplicateEventWhenAlreadyExists() {
        UUID eventId = UUID.randomUUID();
        CommentCreatedEvent event = new CommentCreatedEvent(
            eventId,
            UUID.randomUUID(),
            UUID.randomUUID(),
            null,
            UUID.randomUUID(),
            UUID.randomUUID(),
            "POST_COMMENT",
            Instant.now()
        );

        when(notificationRepository.existsBySourceEventId(eventId)).thenReturn(true);

        consumer.consume(asJson(event));

        verify(notificationRepository, never()).saveAndFlush(any());
    }

    @Test
    void shouldHandleConcurrentDuplicateWhenConstraintViolated() {
        UUID eventId = UUID.randomUUID();
        CommentCreatedEvent event = new CommentCreatedEvent(
            eventId,
            UUID.randomUUID(),
            UUID.randomUUID(),
            null,
            UUID.randomUUID(),
            UUID.randomUUID(),
            "POST_COMMENT",
            Instant.now()
        );

        // Initially not exists
        when(notificationRepository.existsBySourceEventId(eventId))
            .thenReturn(false)
            .thenReturn(true); // check after constraint failure confirms duplicate exists

        when(notificationRepository.saveAndFlush(any(Notification.class)))
            .thenThrow(new DataIntegrityViolationException("Duplicate key constraint"));

        assertThatCode(() -> consumer.consume(asJson(event))).doesNotThrowAnyException();
    }

    @Test
    void shouldIgnoreMalformedJsonWithoutCrashing() {
        String malformedJson = "{not-valid-json";

        assertThatCode(() -> consumer.consume(malformedJson)).doesNotThrowAnyException();
        verify(notificationRepository, never()).saveAndFlush(any());
    }

    @Test
    void shouldIgnoreInvalidEventWithMissingRequiredFields() {
        String invalidPayload = """
            {
                "eventId": null,
                "commentId": "00000000-0000-0000-0000-000000000001",
                "type": "UNKNOWN_TYPE"
            }
            """;

        assertThatCode(() -> consumer.consume(invalidPayload)).doesNotThrowAnyException();
        verify(notificationRepository, never()).saveAndFlush(any());
    }

    @Test
    void shouldPropagateInfrastructureFailureWhenProcessingValidEvent() {
        UUID eventId = UUID.randomUUID();
        CommentCreatedEvent event = new CommentCreatedEvent(
            eventId,
            UUID.randomUUID(),
            UUID.randomUUID(),
            null,
            UUID.randomUUID(),
            UUID.randomUUID(),
            "POST_COMMENT",
            Instant.now()
        );

        when(notificationRepository.existsBySourceEventId(eventId)).thenReturn(false);
        when(notificationRepository.saveAndFlush(any(Notification.class)))
            .thenThrow(new org.springframework.dao.CannotAcquireLockException("Lock acquisition failure"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> consumer.consume(asJson(event)))
            .isInstanceOf(org.springframework.dao.CannotAcquireLockException.class)
            .hasMessageContaining("Lock acquisition failure");
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

        consumer.consume(asJson(event));

        verify(notificationRepository, never()).saveAndFlush(any());
    }

    private String asJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
