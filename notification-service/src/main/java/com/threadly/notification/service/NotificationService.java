package com.threadly.notification.service;

import com.threadly.notification.entity.Notification;
import com.threadly.notification.entity.NotificationType;
import com.threadly.notification.event.CommentCreatedEvent;
import com.threadly.notification.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    /**
     * Processes a comment created event and persists a notification.
     * Note: This method is intentionally NOT annotated with @Transactional to ensure that
     * if saveAndFlush fails with DataIntegrityViolationException, that transaction rolls back immediately,
     * allowing the subsequent existence check to run in a clean transaction.
     */
    public void processCommentCreated(CommentCreatedEvent event) {
        if (notificationRepository.existsBySourceEventId(event.eventId())) {
            log.info("Duplicate event ignored before insert: sourceEventId={}", event.eventId());
            return;
        }

        Notification notification = new Notification(
            UUID.randomUUID(),
            event.eventId(),
            event.recipientUserId(),
            NotificationType.valueOf(event.type()),
            event.actorUserId(),
            event.postId(),
            event.commentId(),
            false,
            event.createdAt()
        );

        try {
            notificationRepository.saveAndFlush(notification);
            log.info("Persisted notification: id={}, sourceEventId={}, userId={}",
                notification.getId(), event.eventId(), event.recipientUserId());
        } catch (DataIntegrityViolationException ex) {
            // Failed insert transaction has already rolled back.
            // Check if it was indeed a duplicate source_event_id.
            if (notificationRepository.existsBySourceEventId(event.eventId())) {
                log.info("Concurrent duplicate event detected and ignored: sourceEventId={}", event.eventId());
            } else {
                // If it was some other constraint failure, rethrow to avoid masking genuine integrity issues.
                log.error("Integrity violation during notification save for sourceEventId={}", event.eventId(), ex);
                throw ex;
            }
        }
    }
}
