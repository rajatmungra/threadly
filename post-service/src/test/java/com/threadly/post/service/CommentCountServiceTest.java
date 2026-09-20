package com.threadly.post.service;

import com.threadly.post.cache.PostFeedCache;
import com.threadly.post.event.CommentCreatedEvent;
import com.threadly.post.repository.PostRepository;
import com.threadly.post.repository.ProcessedCommentEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentCountServiceTest {

    @Mock
    private ProcessedCommentEventRepository processedCommentEventRepository;

    @Mock
    private PostRepository postRepository;

    @Mock
    private PostFeedCache postFeedCache;

    private CommentCountService commentCountService;

    @BeforeEach
    void setUp() {
        commentCountService = new CommentCountService(
            processedCommentEventRepository,
            postRepository,
            postFeedCache
        );
    }

    @Test
    void shouldIncrementCommentCountAndEvictCacheOnFirstProcessing() {
        UUID eventId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        UUID communityId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-09-20T10:00:00Z");

        CommentCreatedEvent event = new CommentCreatedEvent(
            eventId,
            UUID.randomUUID(),
            postId,
            null,
            UUID.randomUUID(),
            UUID.randomUUID(),
            "POST_COMMENT",
            createdAt
        );

        when(processedCommentEventRepository.insertIfNotExists(eventId, postId, createdAt)).thenReturn(1);
        when(postRepository.findCommunityIdByPostId(postId)).thenReturn(Optional.of(communityId));

        commentCountService.processCommentCreated(event);

        verify(processedCommentEventRepository).insertIfNotExists(eventId, postId, createdAt);
        verify(postRepository).incrementCommentCount(postId);
        verify(postFeedCache).evictAfterCommit(communityId);
    }

    @Test
    void shouldIgnoreDuplicateEventWithoutIncrementingOrEvicting() {
        UUID eventId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-09-20T10:00:00Z");

        CommentCreatedEvent event = new CommentCreatedEvent(
            eventId,
            UUID.randomUUID(),
            postId,
            null,
            UUID.randomUUID(),
            UUID.randomUUID(),
            "POST_COMMENT",
            createdAt
        );

        when(processedCommentEventRepository.insertIfNotExists(eventId, postId, createdAt)).thenReturn(0);

        commentCountService.processCommentCreated(event);

        verify(processedCommentEventRepository).insertIfNotExists(eventId, postId, createdAt);
        verify(postRepository, never()).findCommunityIdByPostId(any());
        verify(postRepository, never()).incrementCommentCount(any());
        verify(postFeedCache, never()).evictAfterCommit(any());
    }

    @Test
    void shouldNotIncrementWhenPostNotFound() {
        UUID eventId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-09-20T10:00:00Z");

        CommentCreatedEvent event = new CommentCreatedEvent(
            eventId,
            UUID.randomUUID(),
            postId,
            null,
            UUID.randomUUID(),
            UUID.randomUUID(),
            "POST_COMMENT",
            createdAt
        );

        when(processedCommentEventRepository.insertIfNotExists(eventId, postId, createdAt)).thenReturn(1);
        when(postRepository.findCommunityIdByPostId(postId)).thenReturn(Optional.empty());

        commentCountService.processCommentCreated(event);

        verify(processedCommentEventRepository).insertIfNotExists(eventId, postId, createdAt);
        verify(postRepository).findCommunityIdByPostId(postId);
        verify(postRepository, never()).incrementCommentCount(any());
        verify(postFeedCache, never()).evictAfterCommit(any());
    }
}
