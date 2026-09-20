package com.threadly.notification;

import com.threadly.notification.entity.Notification;
import com.threadly.notification.event.CommentCreatedEvent;
import com.threadly.notification.repository.NotificationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
    "kafka.topics.comment-created=threadly.comment.created.test.v1",
    "spring.kafka.consumer.group-id=notification-service-test"
})
class NotificationKafkaIntegrationTest {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private NotificationRepository notificationRepository;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @Value("${kafka.topics.comment-created:threadly.comment.created.v1}")
    private String topic;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAllInBatch();
    }

    @AfterEach
    void tearDown() {
        notificationRepository.deleteAllInBatch();
    }

    @Test
    void shouldConsumeFromKafkaAndPersistNotificationIdempotently() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID recipientUserId = UUID.randomUUID();
        UUID actorUserId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();

        CommentCreatedEvent event = new CommentCreatedEvent(
            eventId,
            commentId,
            postId,
            null,
            actorUserId,
            recipientUserId,
            "POST_COMMENT",
            Instant.now()
        );

        String payload = objectMapper.writeValueAsString(event);

        // Publish to Kafka
        kafkaTemplate.send(topic, recipientUserId.toString(), payload).get();

        // Wait for consumer to process
        awaitCondition(() -> notificationRepository.existsBySourceEventId(eventId), Duration.ofSeconds(10));

        Optional<Notification> notification = notificationRepository.findBySourceEventId(eventId);
        assertThat(notification).isPresent();
        assertThat(notification.get().getUserId()).isEqualTo(recipientUserId);
        assertThat(notification.get().getCommentId()).isEqualTo(commentId);
        long countBeforeDuplicate = notificationRepository.count();

        // Publish the EXACT SAME event again (duplicate delivery)
        kafkaTemplate.send(topic, recipientUserId.toString(), payload).get();

        // Wait brief period and verify no duplicate row was created
        Thread.sleep(1500);
        assertThat(notificationRepository.count()).isEqualTo(countBeforeDuplicate);
    }

    private void awaitCondition(java.util.function.BooleanSupplier condition, Duration timeout) throws InterruptedException {
        long end = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < end) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(100);
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }
}
