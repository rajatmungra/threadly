package com.threadly.post.consumer;

import com.threadly.post.event.CommentCreatedEvent;
import com.threadly.post.service.CommentCountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CommentCreatedConsumerTest {

    @Mock
    private CommentCountService commentCountService;

    private ObjectMapper objectMapper;
    private CommentCreatedConsumer consumer;

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder().build();
        consumer = new CommentCreatedConsumer(commentCountService, objectMapper);
    }

    @Test
    void shouldProcessValidPostCommentEvent() {
        UUID eventId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();
        UUID recipientUserId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-09-20T10:00:00Z");

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

        consumer.consume(asJson(event));

        verify(commentCountService).processCommentCreated(event);
    }

    @Test
    void shouldProcessValidCommentReplyEvent() {
        UUID eventId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        UUID parentCommentId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();
        UUID recipientUserId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-09-20T11:00:00Z");

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

        consumer.consume(asJson(event));

        verify(commentCountService).processCommentCreated(event);
    }

    @Test
    void shouldRejectUnsupportedEventType() {
        String invalidPayload = """
            {
                "eventId": "11111111-1111-1111-1111-111111111111",
                "commentId": "22222222-2222-2222-2222-222222222222",
                "postId": "33333333-3333-3333-3333-333333333333",
                "type": "UNKNOWN_TYPE"
            }
            """;

        assertThatCode(() -> consumer.consume(invalidPayload)).doesNotThrowAnyException();
        verify(commentCountService, never()).processCommentCreated(any());
    }

    @Test
    void shouldRejectMalformedJsonWithoutCrashing() {
        String malformedJson = "{not-a-valid-json";

        assertThatCode(() -> consumer.consume(malformedJson)).doesNotThrowAnyException();
        verify(commentCountService, never()).processCommentCreated(any());
    }

    @Test
    void shouldRejectMissingRequiredFields() {
        String missingPostId = """
            {
                "eventId": "11111111-1111-1111-1111-111111111111",
                "type": "POST_COMMENT"
            }
            """;

        assertThatCode(() -> consumer.consume(missingPostId)).doesNotThrowAnyException();
        verify(commentCountService, never()).processCommentCreated(any());
    }

    private String asJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
