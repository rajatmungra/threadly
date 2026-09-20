package com.threadly.post.consumer;

import com.threadly.post.event.CommentCreatedEvent;
import com.threadly.post.event.CommentEventType;
import com.threadly.post.service.CommentCountService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@Component
public class CommentCreatedConsumer {

    private static final Logger log = LoggerFactory.getLogger(CommentCreatedConsumer.class);

    private final CommentCountService commentCountService;
    private final ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Autowired
    public CommentCreatedConsumer(CommentCountService commentCountService) {
        this(commentCountService, JsonMapper.builder().build());
    }

    public CommentCreatedConsumer(CommentCountService commentCountService, ObjectMapper objectMapper) {
        this.commentCountService = commentCountService;
        this.objectMapper = objectMapper != null ? objectMapper : JsonMapper.builder().build();
    }

    @KafkaListener(
        topics = "${kafka.topics.comment-created}",
        groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consume(String message) {
        CommentCreatedEvent event;
        try {
            event = objectMapper.readValue(message, CommentCreatedEvent.class);
        } catch (JacksonException ex) {
            log.error("Failed to deserialize comment created event payload: {}", message, ex);
            return;
        }

        if (!isValid(event)) {
            log.error("Received invalid or unsupported comment created event payload: {}", event);
            return;
        }

        commentCountService.processCommentCreated(event);
    }

    private boolean isValid(CommentCreatedEvent event) {
        if (event == null) {
            return false;
        }
        if (event.eventId() == null || event.postId() == null || event.type() == null) {
            return false;
        }
        try {
            CommentEventType.valueOf(event.type());
        } catch (IllegalArgumentException ex) {
            return false;
        }
        return true;
    }
}
