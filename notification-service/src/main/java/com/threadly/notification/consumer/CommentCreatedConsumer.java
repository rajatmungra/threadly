package com.threadly.notification.consumer;

import com.threadly.notification.entity.NotificationType;
import com.threadly.notification.event.CommentCreatedEvent;
import com.threadly.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@Component
public class CommentCreatedConsumer {

    private static final Logger log = LoggerFactory.getLogger(CommentCreatedConsumer.class);

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Autowired
    public CommentCreatedConsumer(NotificationService notificationService) {
        this(notificationService, JsonMapper.builder().build());
    }

    public CommentCreatedConsumer(NotificationService notificationService, ObjectMapper objectMapper) {
        this.notificationService = notificationService;
        this.objectMapper = objectMapper != null ? objectMapper : JsonMapper.builder().build();
    }

    @KafkaListener(
        topics = "${kafka.topics.comment-created:threadly.comment.created.v1}",
        groupId = "${spring.kafka.consumer.group-id:notification-service}"
    )
    public void consume(String message) {
        CommentCreatedEvent event;
        try {
            event = objectMapper.readValue(message, CommentCreatedEvent.class);
        } catch (Exception ex) {
            log.error("Failed to deserialize comment created event payload: {}", message, ex);
            return;
        }

        if (!isValid(event)) {
            log.error("Received invalid comment created event payload: {}", event);
            return;
        }

        // Propagates database/infrastructure errors to allow standard Kafka retry behavior.
        notificationService.processCommentCreated(event);
    }

    private boolean isValid(CommentCreatedEvent event) {
        if (event == null) {
            return false;
        }
        if (event.eventId() == null ||
            event.commentId() == null ||
            event.postId() == null ||
            event.actorUserId() == null ||
            event.recipientUserId() == null ||
            event.createdAt() == null ||
            event.type() == null) {
            return false;
        }
        try {
            NotificationType.valueOf(event.type());
        } catch (IllegalArgumentException ex) {
            return false;
        }
        return true;
    }
}
