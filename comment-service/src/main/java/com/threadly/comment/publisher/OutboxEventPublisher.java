package com.threadly.comment.publisher;

import com.threadly.comment.entity.OutboxEvent;
import com.threadly.comment.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class OutboxEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventPublisher.class);

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final int batchSize;
    private final long kafkaTimeoutMs;

    public OutboxEventPublisher(
        OutboxEventRepository outboxEventRepository,
        KafkaTemplate<String, String> kafkaTemplate,
        @Value("${outbox.batch-size:50}") int batchSize,
        @Value("${outbox.kafka-timeout-ms:5000}") long kafkaTimeoutMs
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.batchSize = batchSize;
        this.kafkaTimeoutMs = kafkaTimeoutMs;
    }

    /**
     * Polls unpublished outbox events and publishes them to Kafka.
     * Note: This method is intentionally NOT @Transactional to avoid holding database
     * transactions open during Kafka network I/O.
     * At-least-once delivery is provided; consumers must be idempotent.
     */
    @Scheduled(fixedDelayString = "${outbox.poll-delay-ms:1000}")
    public void publishPendingEvents() {
        List<OutboxEvent> pendingEvents = outboxEventRepository.findByPublishedAtIsNullOrderByCreatedAtAsc(
            PageRequest.of(0, batchSize)
        );

        if (pendingEvents.isEmpty()) {
            return;
        }

        for (OutboxEvent event : pendingEvents) {
            try {
                kafkaTemplate.send(event.getTopic(), event.getMessageKey(), event.getPayload())
                    .get(kafkaTimeoutMs, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                log.error("Outbox publisher interrupted while publishing event id={}", event.getId(), ex);
                break;
            } catch (ExecutionException | TimeoutException | KafkaException | CancellationException ex) {
                log.error("Failed to publish outbox event id={} to Kafka. Halting batch processing for this cycle.",
                    event.getId(), ex);
                break;
            }

            try {
                outboxEventRepository.markPublished(event.getId(), Instant.now());
                log.debug("Published outbox event: id={}, topic={}", event.getId(), event.getTopic());
            } catch (DataAccessException ex) {
                log.error("Kafka accepted outbox event id={}, but updating published_at failed. Event remains unpublished and may be republished; consumer idempotency handles duplicate deliveries. Halting batch processing for this cycle.",
                    event.getId(), ex);
                break;
            }
        }
    }
}
