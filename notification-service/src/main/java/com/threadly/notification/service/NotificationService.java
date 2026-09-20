package com.threadly.notification.service;

import com.threadly.notification.dto.response.NotificationResponse;
import com.threadly.notification.dto.response.PagedResponse;
import com.threadly.notification.entity.Notification;
import com.threadly.notification.entity.NotificationType;
import com.threadly.notification.event.CommentCreatedEvent;
import com.threadly.notification.exception.InvalidPaginationException;
import com.threadly.notification.exception.NotificationNotFoundException;
import com.threadly.notification.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional(readOnly = true)
    public PagedResponse<NotificationResponse> getNotifications(UUID userId, int page, int size) {
        validatePagination(page, size);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<Notification> notificationPage = notificationRepository.findByUserId(userId, pageable);

        List<NotificationResponse> content = notificationPage.getContent().stream()
            .map(this::toResponse)
            .toList();

        return new PagedResponse<>(
            content,
            notificationPage.getNumber(),
            notificationPage.getSize(),
            notificationPage.getTotalElements(),
            notificationPage.getTotalPages(),
            notificationPage.isLast()
        );
    }

    @Transactional
    public void markAsRead(UUID notificationId, UUID userId) {
        Notification notification = notificationRepository.findByIdAndUserId(notificationId, userId)
            .orElseThrow(() -> new NotificationNotFoundException("Notification not found: " + notificationId));

        if (!notification.isRead()) {
            notification.setRead(true);
        }
    }

    private void validatePagination(int page, int size) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        if (page < 0) {
            fieldErrors.put("page", "Page index must not be less than zero");
        }
        if (size < 1) {
            fieldErrors.put("size", "Page size must be at least 1");
        } else if (size > 100) {
            fieldErrors.put("size", "Page size must not exceed 100");
        }
        if (!fieldErrors.isEmpty()) {
            throw new InvalidPaginationException("Invalid pagination parameters", fieldErrors);
        }
    }

    private NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
            notification.getId(),
            notification.getType(),
            notification.getActorUserId(),
            notification.getPostId(),
            notification.getCommentId(),
            notification.isRead(),
            notification.getCreatedAt()
        );
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
