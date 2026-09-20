package com.threadly.post.repository;

import com.threadly.post.entity.ProcessedCommentEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface ProcessedCommentEventRepository extends JpaRepository<ProcessedCommentEvent, UUID> {

    @Modifying
    @Query(value = "INSERT INTO processed_comment_events (event_id, post_id, processed_at) " +
                   "VALUES (:eventId, :postId, :processedAt) " +
                   "ON CONFLICT (event_id) DO NOTHING",
           nativeQuery = true)
    int insertIfNotExists(@Param("eventId") UUID eventId,
                          @Param("postId") UUID postId,
                          @Param("processedAt") Instant processedAt);
}
