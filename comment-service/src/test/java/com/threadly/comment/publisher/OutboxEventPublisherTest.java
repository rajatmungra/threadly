package com.threadly.comment.publisher;

import com.threadly.comment.entity.OutboxEvent;
import com.threadly.comment.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxEventPublisherTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private OutboxEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new OutboxEventPublisher(outboxEventRepository, kafkaTemplate, 50, 5000L);
    }

    @Test
    void shouldPublishPendingEventAndMarkPublished() {
        UUID eventId = UUID.randomUUID();
        String topic = "threadly.comment.created.v1";
        String key = UUID.randomUUID().toString();
        String payload = "{\"eventId\":\"" + eventId + "\"}";

        OutboxEvent event = new OutboxEvent(
            eventId,
            topic,
            key,
            "POST_COMMENT",
            payload,
            Instant.now(),
            null
        );

        when(outboxEventRepository.findByPublishedAtIsNullOrderByCreatedAtAsc(PageRequest.of(0, 50)))
            .thenReturn(List.of(event));

        CompletableFuture<SendResult<String, String>> future = CompletableFuture.completedFuture(mock(SendResult.class));
        when(kafkaTemplate.send(topic, key, payload)).thenReturn(future);

        publisher.publishPendingEvents();

        verify(kafkaTemplate).send(topic, key, payload);
        verify(outboxEventRepository).markPublished(eq(eventId), any(Instant.class));
    }

    @Test
    void shouldLeaveUnpublishedAndHaltBatchOnKafkaFailure() {
        UUID event1Id = UUID.randomUUID();
        UUID event2Id = UUID.randomUUID();
        String topic = "threadly.comment.created.v1";

        OutboxEvent event1 = new OutboxEvent(event1Id, topic, "key1", "POST_COMMENT", "{}", Instant.now(), null);
        OutboxEvent event2 = new OutboxEvent(event2Id, topic, "key2", "POST_COMMENT", "{}", Instant.now(), null);

        when(outboxEventRepository.findByPublishedAtIsNullOrderByCreatedAtAsc(PageRequest.of(0, 50)))
            .thenReturn(List.of(event1, event2));

        CompletableFuture<SendResult<String, String>> failingFuture = new CompletableFuture<>();
        failingFuture.completeExceptionally(new TimeoutException("Kafka broker unreachable"));
        when(kafkaTemplate.send(topic, "key1", "{}")).thenReturn(failingFuture);

        publisher.publishPendingEvents();

        verify(outboxEventRepository, never()).markPublished(eq(event1Id), any(Instant.class));
        // Verify event2 is NOT attempted due to fail-fast behavior
        verify(kafkaTemplate, never()).send(topic, "key2", "{}");
        verify(outboxEventRepository, never()).markPublished(eq(event2Id), any(Instant.class));
    }

    @Test
    void shouldDoNothingWhenNoPendingEvents() {
        when(outboxEventRepository.findByPublishedAtIsNullOrderByCreatedAtAsc(PageRequest.of(0, 50)))
            .thenReturn(List.of());

        publisher.publishPendingEvents();

        verify(kafkaTemplate, never()).send(any(), any(), any());
    }

    @Test
    void shouldHaltBatchAndLeaveRowUnpublishedWhenMarkPublishedFailsWithDataAccessException() {
        UUID event1Id = UUID.randomUUID();
        UUID event2Id = UUID.randomUUID();
        String topic = "threadly.comment.created.v1";

        OutboxEvent event1 = new OutboxEvent(event1Id, topic, "key1", "POST_COMMENT", "{}", Instant.now(), null);
        OutboxEvent event2 = new OutboxEvent(event2Id, topic, "key2", "POST_COMMENT", "{}", Instant.now(), null);

        when(outboxEventRepository.findByPublishedAtIsNullOrderByCreatedAtAsc(PageRequest.of(0, 50)))
            .thenReturn(List.of(event1, event2));

        CompletableFuture<SendResult<String, String>> future = CompletableFuture.completedFuture(mock(SendResult.class));
        when(kafkaTemplate.send(topic, "key1", "{}")).thenReturn(future);

        org.mockito.Mockito.doThrow(new org.springframework.dao.TransientDataAccessResourceException("DB connection error"))
            .when(outboxEventRepository).markPublished(eq(event1Id), any(Instant.class));

        publisher.publishPendingEvents();

        verify(kafkaTemplate).send(topic, "key1", "{}");
        verify(outboxEventRepository).markPublished(eq(event1Id), any(Instant.class));
        // Verify subsequent event in batch is NOT processed
        verify(kafkaTemplate, never()).send(topic, "key2", "{}");
        verify(outboxEventRepository, never()).markPublished(eq(event2Id), any(Instant.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldRestoreInterruptFlagAndHaltBatchWhenInterruptedDuringKafkaPublish() throws Exception {
        UUID event1Id = UUID.randomUUID();
        UUID event2Id = UUID.randomUUID();
        String topic = "threadly.comment.created.v1";

        OutboxEvent event1 = new OutboxEvent(event1Id, topic, "key1", "POST_COMMENT", "{}", Instant.now(), null);
        OutboxEvent event2 = new OutboxEvent(event2Id, topic, "key2", "POST_COMMENT", "{}", Instant.now(), null);

        when(outboxEventRepository.findByPublishedAtIsNullOrderByCreatedAtAsc(PageRequest.of(0, 50)))
            .thenReturn(List.of(event1, event2));

        CompletableFuture<SendResult<String, String>> future = mock(CompletableFuture.class);
        when(future.get(anyLong(), any(TimeUnit.class))).thenThrow(new InterruptedException("Thread interrupted"));
        when(kafkaTemplate.send(topic, "key1", "{}")).thenReturn(future);

        publisher.publishPendingEvents();

        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        // Clear interrupted status so other tests are not affected
        Thread.interrupted();

        verify(outboxEventRepository, never()).markPublished(eq(event1Id), any(Instant.class));
        verify(kafkaTemplate, never()).send(topic, "key2", "{}");
    }

    @Test
    void shouldHaltBatchOnKafkaException() {
        UUID event1Id = UUID.randomUUID();
        String topic = "threadly.comment.created.v1";
        OutboxEvent event1 = new OutboxEvent(event1Id, topic, "key1", "POST_COMMENT", "{}", Instant.now(), null);

        when(outboxEventRepository.findByPublishedAtIsNullOrderByCreatedAtAsc(PageRequest.of(0, 50)))
            .thenReturn(List.of(event1));

        when(kafkaTemplate.send(topic, "key1", "{}")).thenThrow(new org.springframework.kafka.KafkaException("Broker connection error"));

        publisher.publishPendingEvents();

        verify(outboxEventRepository, never()).markPublished(eq(event1Id), any(Instant.class));
    }
}
